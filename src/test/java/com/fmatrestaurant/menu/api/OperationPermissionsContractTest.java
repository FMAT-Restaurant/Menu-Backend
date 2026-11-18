package com.fmatrestaurant.menu.api;

import static com.fmatrestaurant.menu.TestTokens.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fmatrestaurant.menu.TestTokens;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.api.security.OperationPermissions;
import com.fmatrestaurant.menu.api.security.OperationPermissions.Kind;
import com.fmatrestaurant.menu.api.security.OperationPermissions.Rule;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;

/**
 * TC-GEN-01-CA5: the permissions Menu requires are exactly the {@code x-required-permission} of the served
 * contract, and a credential with only the permission of an operation reaches it. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class OperationPermissionsContractTest {

	private static final Set<String> INVENTORY_PATHS = Set.of("/items", "/items/{id}");

	private static final String VERSIONED_PREFIX = ApiVersioning.PREFIX + "/{version}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	@DisplayName("TC-GEN-01-CA5 — Cada operación exige exactamente su x-required-permission; hay 13 permisos")
	void permissionsMatchTheContract() {
		Map<String, ContractOperation> operations = contractOperations();
		List<String> mismatches = new ArrayList<>();
		Set<String> distinct = new TreeSet<>();
		operations.forEach((operationId, operation) -> {
			distinct.addAll(operation.permissions());
			Optional<Rule> rule = OperationPermissions.find(operation.method().name(), concrete(operation.path()));
			if (rule.isEmpty() || !rule.get().permissions().equals(operation.permissions())) {
				mismatches.add(operationId + ": contract " + operation.permissions() + ", Menu "
						+ rule.map(Rule::permissions).orElse(null));
			}
		});
		assertTrue(mismatches.isEmpty(), () -> "Permissions that differ from the contract: " + mismatches);
		assertEquals(operations.size(), OperationPermissions.RULES.size(), "Rules that the contract does not define");
		assertEquals(13, distinct.size(), () -> "Permissions of the contract: " + distinct);
		OperationPermissions.RULES.stream().filter(rule -> rule.kind() != Kind.FIXED).forEach(rule -> assertEquals(2,
				rule.permissions().size(), () -> rule + " must hold the management and the status permission"));
	}

	@Test
	@DisplayName("Cada handler de la API de Menu tiene su regla de permisos")
	void everyHandlerHasARule() {
		Set<String> unprotected = new TreeSet<>();
		for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
			for (String pattern : info.getPatternValues()) {
				if (!pattern.startsWith(VERSIONED_PREFIX + "/")) {
					continue;
				}
				String path = pattern.substring(VERSIONED_PREFIX.length());
				if (INVENTORY_PATHS.contains(path)) {
					continue;
				}
				info.getMethodsCondition().getMethods().forEach(method -> {
					if (OperationPermissions.find(method.name(), concrete(path)).isEmpty()) {
						unprotected.add(method + " " + path);
					}
				});
			}
		}
		assertTrue(unprotected.isEmpty(), () -> "Handlers without a permission rule: " + unprotected);
	}

	@Test
	@DisplayName("TC-GEN-01-CA5 — Con solo su permiso, ninguna operación responde 403; sin permisos, todas")
	void onlyItsPermissionReachesEachOperation() throws Exception {
		List<String> failures = new ArrayList<>();
		for (ContractOperation operation : contractOperations().values()) {
			List<String> grants = operation.permissions().size() == 1 ? List.copyOf(operation.permissions())
					: List.of(operation.permissions().stream().filter(p -> p.endsWith(":write")).findFirst()
							.orElseThrow(), operation.permissions().stream().filter(p -> p.endsWith(":status"))
							.findFirst().orElseThrow());
			for (String permission : grants) {
				int status = perform(operation, permission, TestTokens.with(permission));
				if (status == 401 || status == 403) {
					failures.add(operation + " with " + permission + " -> " + status);
				}
			}
			int denied = perform(operation, grants.getFirst(), TestTokens.NONE);
			if (denied != 403) {
				failures.add(operation + " without permissions -> " + denied);
			}
		}
		assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
	}

	/** Bodies are minimal: authorization runs before validation, so any answer but 401 or 403 is enough. */
	private int perform(ContractOperation operation, String permission, String token) throws Exception {
		MockHttpServletRequestBuilder request = request(operation.method(),
				ApiVersioning.BASE + concrete(operation.path()))
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.header("Idempotency-Key", UUID.randomUUID())
				.header(HttpHeaders.IF_MATCH, "\"rev-0\"");
		if (operation.method() == HttpMethod.POST || operation.method() == HttpMethod.PATCH) {
			String body = permission.endsWith(":status") ? "{\"status\":\"ACTIVE\"}" : "{\"name\":\"x\"}";
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request).andReturn().getResponse().getStatus();
	}

	/** Every operation of Menu in the served contract, without those of Inventario. */
	private static Map<String, ContractOperation> contractOperations() {
		OpenAPI contract = new OpenAPIV3Parser().read("static/openapi.yaml");
		Map<String, ContractOperation> operations = new TreeMap<>();
		contract.getPaths().forEach((path, item) -> {
			if (INVENTORY_PATHS.contains(path)) {
				return;
			}
			for (Map.Entry<PathItem.HttpMethod, Operation> entry : item.readOperationsMap().entrySet()) {
				Object declared = entry.getValue().getExtensions().get("x-required-permission");
				Set<String> permissions = new TreeSet<>();
				if (declared instanceof List<?> list) {
					list.forEach(permission -> permissions.add(String.valueOf(permission)));
				}
				operations.put(entry.getValue().getOperationId(), new ContractOperation(
						HttpMethod.valueOf(entry.getKey().name()), path, Set.copyOf(permissions)));
			}
		});
		return operations;
	}

	/** A path template with every variable replaced by a value that parses as a UUID or a number. */
	private static String concrete(String path) {
		return path.replace("{revision}", "1").replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
	}

	private record ContractOperation(HttpMethod method, String path, Set<String> permissions) {

		@Override
		public String toString() {
			return method + " " + path;
		}

	}

}
