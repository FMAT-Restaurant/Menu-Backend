package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.TestSecurityConfiguration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;

@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

	private static final String CONTRACT_VERSION = "8.0.1";

	/**
	 * Operations of the contract that are not implemented yet. Each Feature ticket removes its own when it
	 * adds the handler; the test fails if one is implemented and still listed.
	 */
	private static final Set<String> PENDING = Set.of(
			// BE-14 (#65)
			"getMenuCatalog", "listMenuCatalogCategories",
			// BE-15 (#66)
			"getPrintableMenu",
			// BE-16 (#67)
			"listMenuRecipeRevisions", "getMenuRecipeRevision",
			// BE-17 (#68)
			"replaceMenuImageFile");

	/** Paths of Inventario, referenced by the contract but served by the Inventory microservice. */
	private static final Set<String> INVENTORY_PATHS = Set.of("/items", "/items/{}");

	private static final String VERSIONED_PREFIX = ApiVersioning.PREFIX + "/{version}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	@DisplayName("GET /openapi.yaml sirve el bundle autocontenido del contrato " + CONTRACT_VERSION)
	void servesTheCurrentContractBundle() throws Exception {
		mockMvc.perform(get("/openapi.yaml"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("version: " + CONTRACT_VERSION)))
				.andExpect(content().string(Matchers.not(Matchers.containsString(".yaml#"))));
	}

	@Test
	@DisplayName("Cada operación del contrato tiene un handler, salvo las pendientes explícitas")
	void everyContractOperationHasAHandler() {
		Map<String, String> operations = contractOperations();
		Set<String> handlers = handlerOperations();

		Set<String> missing = new TreeSet<>();
		Set<String> implementedButPending = new TreeSet<>();
		operations.forEach((operationId, signature) -> {
			boolean implemented = handlers.contains(signature);
			if (!implemented && !PENDING.contains(operationId)) {
				missing.add(operationId + " (" + signature + ")");
			}
			if (implemented && PENDING.contains(operationId)) {
				implementedButPending.add(operationId);
			}
		});
		Set<String> unknownPending = new TreeSet<>(PENDING);
		unknownPending.removeAll(operations.keySet());

		assertTrue(missing.isEmpty(), () -> "Operations of the contract without a handler: " + missing);
		assertTrue(implementedButPending.isEmpty(),
				() -> "Implemented operations still listed as pending: " + implementedButPending);
		assertEquals(Set.of(), unknownPending, "Pending operations that the contract does not define");
	}

	/** {@code operationId} to {@code "METHOD /path/{}"}, without the operations of Inventario. */
	private static Map<String, String> contractOperations() {
		OpenAPI contract = new OpenAPIV3Parser().read("static/openapi.yaml");
		Map<String, String> operations = new TreeMap<>();
		contract.getPaths().forEach((path, item) -> {
			String normalized = normalize(path);
			if (INVENTORY_PATHS.contains(normalized)) {
				return;
			}
			for (Map.Entry<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operation
					: item.readOperationsMap().entrySet()) {
				operations.put(operation.getValue().getOperationId(), operation.getKey() + " " + normalized);
			}
		});
		return operations;
	}

	/** {@code "METHOD /path/{}"} of every handler of the Menu API. */
	private Set<String> handlerOperations() {
		Set<String> signatures = new TreeSet<>();
		for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
			for (String pattern : info.getPatternValues()) {
				if (!pattern.startsWith(VERSIONED_PREFIX + "/")) {
					continue;
				}
				String path = normalize(pattern.substring(VERSIONED_PREFIX.length()));
				info.getMethodsCondition().getMethods()
						.forEach(method -> signatures.add(method.name() + " " + path));
			}
		}
		return signatures;
	}

	/** Path variables become {@code {}}, so their names do not matter. */
	private static String normalize(String path) {
		return path.replaceAll("\\{[^}]+}", "{}");
	}

}
