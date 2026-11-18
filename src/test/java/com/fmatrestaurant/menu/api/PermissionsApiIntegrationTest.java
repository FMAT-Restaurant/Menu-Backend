package com.fmatrestaurant.menu.api;

import static com.fmatrestaurant.menu.TestTokens.bearer;
import static com.fmatrestaurant.menu.api.ContractValidation.conformsToContract;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.TestTokens;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.Category;
import com.fmatrestaurant.menu.domain.model.EntryStatus;
import com.fmatrestaurant.menu.domain.model.Image;
import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.IdempotencyRecordRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ImageRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Authorization by the {@code permissions} of the Bearer credential (BE-13, RF-GEN-01). Every request sets
 * its own credential: this class does not import the TK-ADMIN default. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class PermissionsApiIntegrationTest {

	private static final String API = ApiVersioning.BASE + "/menu";

	private static final String CATEGORIES = API + "/categories";

	private static final InventoryItem POTATOES = new InventoryItem("INV-potatoes-001", "Potatoes", "g");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private IdempotencyRecordRepository idempotencyRecords;

	private UUID imageId;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws IOException {
		InventoryStub.INSTANCE.reset();
		InventoryStub.INSTANCE.add(POTATOES);
		idempotencyRecords.deleteAll();
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
		imageRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png())).getId();
	}

	@Test
	@DisplayName("TC-GEN-01-CA1 — Sin menu:categories:write, crear una categoría responde 403 sin ETag")
	void creatingACategoryRequiresItsPermission() throws Exception {
		createCategory(TestTokens.allBut("menu:categories:write"), UUID.randomUUID(), "Categoría GEN-01-CA1")
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ETAG))
				.andExpect(jsonPath("$.error.message").isString())
				.andExpect(conformsToContract());
		assertEquals(0, categoryRepository.count());
		assertEquals(0, idempotencyRecords.count());
	}

	@Test
	@DisplayName("TC-GEN-01-CA2 — Una operación sin permiso se rechaza sin cambios")
	void anOperationWithoutPermissionChangesNothing() throws Exception {
		Category category = categoryRepository.save(new Category(1L, "Bebidas GEN-01-CA2", "Bebidas frías."));

		mockMvc.perform(patch(CATEGORIES + "/" + category.getId()).header(HttpHeaders.AUTHORIZATION,
				bearer(TestTokens.CATALOG)).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Bebidas cambiadas\",\"description\":\"Otra descripción.\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.message").isString())
				.andExpect(conformsToContract());

		Category unchanged = categoryRepository.findById(category.getId()).orElseThrow();
		assertEquals("bebidas gen-01-ca2", unchanged.getName().toLowerCase());
		assertEquals("Bebidas frías.", unchanged.getDescription());
	}

	@ParameterizedTest(name = "[{index}] solo {0}")
	@ValueSource(strings = { "menu:entries:write", "menu:entries:status" })
	@DisplayName("TC-GEN-01-CA3a/CA3b — Mezclar estado y edición en una entrada exige ambos permisos")
	void mixingStatusAndEditionRequiresBothPermissions(String permission) throws Exception {
		CatalogEntry entry = entry("Entrada GEN-01-CA3");

		patchEntry(entry, TestTokens.with(permission), "{\"status\":\"ARCHIVED\",\"brandName\":\"Entrada editada\"}")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.message").isString())
				.andExpect(conformsToContract());

		CatalogEntry unchanged = entryRepository.findById(entry.getId()).orElseThrow();
		assertEquals(EntryStatus.INACTIVE, unchanged.getStatus());
		assertEquals("Entrada GEN-01-CA3", unchanged.getBrandName());
	}

	@ParameterizedTest(name = "[{index}] {0} con {1}")
	@CsvSource(delimiter = '|', value = {
			"{\"status\":\"ARCHIVED\"}                            | menu:entries:status                    | 200",
			"{\"status\":\"ARCHIVED\"}                            | menu:entries:write                     | 403",
			"{\"brandName\":\"Otra\"}                             | menu:entries:write                     | 200",
			"{\"brandName\":\"Otra\"}                             | menu:entries:status                    | 403",
			"{\"status\":\"ARCHIVED\",\"brandName\":\"Otra\"}     | menu:entries:write,menu:entries:status | 200" })
	@DisplayName("RF-GEN-01.CA3 — El cuerpo del PATCH de una entrada decide qué permiso exige")
	void theBodyOfAnEntryPatchDecidesThePermission(String body, String permissions, int expected) throws Exception {
		CatalogEntry entry = entry("Entrada PATCH");

		patchEntry(entry, TestTokens.with(permissions.split(",")), body)
				.andExpect(status().is(expected));
	}

	@Test
	@DisplayName("RF-GEN-01.CA3 — Cambiar el estado de una opción de una oferta exige menu:offers:status")
	void changingTheStatusOfAnOptionRequiresTheStatusPermission() throws Exception {
		CatalogEntry entry = entry("Entrada con oferta");
		String created = mockMvc.perform(post(API + "/entries/" + entry.getId() + "/offers")
				.header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.ADMIN))
				.header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"presentationTag": "Individual", "basePrice": 180, "imageId": "%s", "composition": {"slots": [
						  {"name": "Side", "quantity": 1, "options": [{"displayName": "Fries",
						   "sourceType": "INVENTORY_ITEM", "inventoryItemId": "%s", "quantity": 200}]}]}}
						""".formatted(imageId, POTATOES.id())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String offerId = JsonPath.read(created, "$.data.id");
		String slotId = JsonPath.read(created, "$.data.composition.slots[0].slotId");
		String optionId = JsonPath.read(created, "$.data.composition.slots[0].options[0].optionId");
		String deactivate = """
				{"composition": {"slots": [{"slotId": "%s", "options": [
				  {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "status": "INACTIVE"}]}]}}
				""".formatted(slotId, optionId);
		String deactivateAndRename = deactivate.replace("\"status\"", "\"displayName\": \"Papas\", \"status\"");

		patchOffer(offerId, TestTokens.with("menu:offers:write"), deactivate)
				.andExpect(status().isForbidden());
		patchOffer(offerId, TestTokens.with("menu:offers:status"), deactivateAndRename)
				.andExpect(status().isForbidden());
		patchOffer(offerId, TestTokens.with("menu:offers:status"), deactivate)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].status").value("INACTIVE"));
	}

	@ParameterizedTest(name = "[{index}] {0} con {1}")
	@CsvSource(delimiter = '|', value = {
			"/categories | menu:categories:read", "/entries | menu:entries:read",
			"/offers     | menu:offers:read", "/recipes | menu:recipes:read" })
	@DisplayName("TC-CONS-04-CA6 — Cada consulta administrativa requiere su permiso de lectura")
	void eachAdministrativeListRequiresItsReadPermission(String path, String permission) throws Exception {
		mockMvc.perform(get(API + path).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.message").isString())
				.andExpect(conformsToContract());
		mockMvc.perform(get(API + path).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.with(permission))))
				.andExpect(status().isOk());
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@ValueSource(strings = { "", "Bearer xyz", "Bearer a.b.c", "Basic dXNlcjpwYXNz", "MALFORMED" })
	@DisplayName("Sin credencial, con una que no es un JWT o con permissions mal formado responde 401")
	void unreadableCredentialsAreUnauthorized(String authorization) throws Exception {
		var request = get(CATEGORIES);
		if ("MALFORMED".equals(authorization)) {
			request.header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.MALFORMED_PERMISSIONS));
		} else if (!authorization.isEmpty()) {
			request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		mockMvc.perform(request)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.message").isString())
				.andExpect(conformsToContract());
	}

	@Test
	@DisplayName("Un token sin permissions o con la lista vacía responde 403")
	void missingOrEmptyPermissionsAreForbidden() throws Exception {
		for (String token : List.of(TestTokens.WITHOUT_PERMISSIONS, TestTokens.NONE)) {
			mockMvc.perform(get(CATEGORIES).header(HttpHeaders.AUTHORIZATION, bearer(token)))
					.andExpect(status().isForbidden())
					.andExpect(conformsToContract());
		}
	}

	@Test
	@DisplayName("El comodín \"*\" concede todos los permisos")
	void theWildcardGrantsEverything() throws Exception {
		createCategory(TestTokens.WILDCARD, UUID.randomUUID(), "Postres").andExpect(status().isCreated());
		mockMvc.perform(get(CATEGORIES).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.WILDCARD)))
				.andExpect(status().isOk());
		patchEntry(entry("Entrada comodín"), TestTokens.WILDCARD, "{\"status\":\"ARCHIVED\",\"brandName\":\"X\"}")
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("El permiso se comprueba antes de la idempotencia: un 403 no liga la Idempotency-Key")
	void permissionsAreCheckedBeforeIdempotency() throws Exception {
		mockMvc.perform(post(CATEGORIES).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sin clave\"}"))
				.andExpect(status().isForbidden());

		UUID key = UUID.randomUUID();
		createCategory(TestTokens.CATALOG, key, "Primera").andExpect(status().isForbidden());
		assertEquals(0, idempotencyRecords.count());

		// The same key with another body would be 409 if the 403 had bound it.
		createCategory(TestTokens.ADMIN, key, "Otra").andExpect(status().isCreated());
		assertEquals(1, categoryRepository.count());
	}

	@Test
	@DisplayName("El contrato, el preflight CORS y el mock de Inventario no requieren credencial")
	void publicRoutesNeedNoCredential() throws Exception {
		mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
		mockMvc.perform(options(CATEGORIES).header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
	}

	@Test
	@DisplayName("Los 401 y 403 conservan las cabeceras CORS")
	void securityErrorsKeepCorsHeaders() throws Exception {
		mockMvc.perform(get(CATEGORIES).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
		mockMvc.perform(get(CATEGORIES).header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG)))
				.andExpect(status().isForbidden())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
	}

	private ResultActions createCategory(String token, UUID key, String name) throws Exception {
		return mockMvc.perform(post(CATEGORIES).header(HttpHeaders.AUTHORIZATION, bearer(token))
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"));
	}

	private CatalogEntry entry(String brandName) {
		return entryRepository.save(new CatalogEntry(1L, brandName, "Descripción.", imageId, List.of()));
	}

	private ResultActions patchEntry(CatalogEntry entry, String token, String body) throws Exception {
		return mockMvc.perform(patch(API + "/entries/" + entry.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token))
				.header(HttpHeaders.IF_MATCH, "\"rev-" + entry.getVersion() + "\"")
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions patchOffer(String offerId, String token, String body) throws Exception {
		String etag = mockMvc.perform(get(API + "/offers/" + offerId)
				.header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.ADMIN)))
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		return mockMvc.perform(patch(API + "/offers/" + offerId).header(HttpHeaders.AUTHORIZATION, bearer(token))
				.header("Idempotency-Key", UUID.randomUUID()).header(HttpHeaders.IF_MATCH, etag)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
