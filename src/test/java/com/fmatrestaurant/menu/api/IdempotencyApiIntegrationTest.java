package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.IdempotencyRecordRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Idempotency-Key of the creations and of PATCH of offers, against PostgreSQL (Testcontainers) and
 * {@link InventoryStub}. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class IdempotencyApiIntegrationTest {

	private static final String KEY = "Idempotency-Key";

	private static final String CATEGORIES = "/api/v1/menu/categories";

	private static final String CODE = "$.error.details.code";

	private static final InventoryItem BEEF = new InventoryItem("INV-beef-001", "Beef", "g");

	private static final InventoryStub INVENTORY = InventoryStub.INSTANCE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private IdempotencyRecordRepository records;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RecipeRevisionRepository revisionRepository;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID imageId;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws IOException {
		INVENTORY.reset();
		INVENTORY.add(BEEF);
		records.deleteAll();
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
		revisionRepository.deleteAll();
		recipeRepository.deleteAll();
		imageRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png(20))).getId();
	}

	static Stream<Arguments> operations() {
		UUID id = UUID.randomUUID();
		return Stream.of(Arguments.of(HttpMethod.POST, CATEGORIES), Arguments.of(HttpMethod.POST, "/api/v1/menu/entries"),
				Arguments.of(HttpMethod.POST, "/api/v1/menu/entries/" + id + "/offers"),
				Arguments.of(HttpMethod.POST, "/api/v1/menu/recipes"), Arguments.of(HttpMethod.POST, "/api/v1/media/images"),
				Arguments.of(HttpMethod.PATCH, "/api/v1/menu/offers/" + id));
	}

	@ParameterizedTest
	@MethodSource("operations")
	void requiresAnUnquotedUuidKey(HttpMethod method, String path) throws Exception {
		for (String key : new String[] { null, "\"" + UUID.randomUUID() + "\"", "not-a-uuid" }) {
			MockHttpServletRequestBuilder request = request(method, path).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
					.contentType(MediaType.APPLICATION_JSON).content("{}");
			if (key != null) {
				request.header(KEY, key);
			}
			mockMvc.perform(request)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath(CODE).value("INVALID_IDEMPOTENCY_KEY"));
		}
	}

	@Test
	void otherOperationsDoNotRequireAKey() throws Exception {
		Category category = categoryRepository.save(new Category(1L, "Drinks", "Cold"));
		mockMvc.perform(get(CATEGORIES)).andExpect(status().isOk());
		mockMvc.perform(patch(CATEGORIES + "/" + category.getId()).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Juices\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete(CATEGORIES + "/" + category.getId()).header(HttpHeaders.IF_MATCH, "\"rev-1\""))
				.andExpect(status().isNoContent());
	}

	@Test
	void replaysTheOriginalCreationEvenAfterTheResourceIsDeleted() throws Exception {
		String key = UUID.randomUUID().toString();
		MvcResult first = createCategory(key, "{\"name\": \"Desserts\", \"description\": \"Sweet\"}")
				.andExpect(status().isCreated()).andReturn();
		String id = JsonPath.read(first.getResponse().getContentAsString(), "$.data.id");

		// Whitespace and property order do not change the request.
		MvcResult replay = createCategory(key, "{ \"description\" : \"Sweet\",\n \"name\" : \"Desserts\" }")
				.andExpect(status().isCreated()).andReturn();
		assertSameResponse(first, replay);
		assertEquals(1, categoryRepository.count());

		mockMvc.perform(delete(CATEGORIES + "/" + id).header(HttpHeaders.IF_MATCH, "\"rev-0\""))
				.andExpect(status().isNoContent());
		assertSameResponse(first, createCategory(key, "{\"name\": \"Desserts\", \"description\": \"Sweet\"}")
				.andExpect(status().isCreated()).andReturn());
		assertEquals(0, categoryRepository.count());
	}

	@Test
	void rejectsADifferentRequestWithTheSameKey() throws Exception {
		String key = UUID.randomUUID().toString();
		createCategory(key, "{\"name\": \"Desserts\"}").andExpect(status().isCreated());

		expectReused(createCategory(key, "{\"name\": \"Desserts\", \"description\": null}"));
		expectReused(createCategory(key, "{\"name\": \"Starters\"}"));
		// The same key on another route is another operation.
		mockMvc.perform(post("/api/v1/menu/recipes").header(KEY, key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Burger\", \"description\": \"x\", \"instructions\": \"y\", \"supplies\": "
						+ "[{\"inventoryItemId\": \"" + BEEF.id() + "\", \"quantity\": 150}]}"))
				.andExpect(status().isCreated());
	}

	@Test
	void arrayOrderChangesTheRequest() throws Exception {
		UUID drinks = categoryRepository.save(new Category(1L, "Drinks", "Cold")).getId();
		UUID food = categoryRepository.save(new Category(1L, "Food", "Hot")).getId();
		String key = UUID.randomUUID().toString();
		createEntry(key, drinks, food).andExpect(status().isCreated());
		createEntry(key, drinks, food).andExpect(status().isCreated());
		expectReused(createEntry(key, food, drinks));
		assertEquals(1, entryRepository.count());
	}

	@Test
	void anExpiredKeyCanBeUsedAgain() throws Exception {
		String key = UUID.randomUUID().toString();
		createCategory(key, "{\"name\": \"Desserts\"}").andExpect(status().isCreated());
		jdbc.update("update idempotency_record set created_at = created_at - interval '25 hours', "
				+ "completed_at = completed_at - interval '25 hours'");

		createCategory(key, "{\"name\": \"Starters\"}").andExpect(status().isCreated());
		assertEquals(2, categoryRepository.count());
	}

	@Test
	void replaysAPatchOfAnOfferBeforeCheckingTheCurrentEtag() throws Exception {
		String offer = JsonPath.read(mockMvc.perform(post("/api/v1/menu/entries/" + entry() + "/offers")
				.header(KEY, UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(offerBody()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
		String key = UUID.randomUUID().toString();
		String rename = "{\"presentationTag\": \"Double\"}";

		mockMvc.perform(patch("/api/v1/menu/offers/" + offer).header(KEY, key)
				.contentType(MediaType.APPLICATION_JSON).content(rename))
				.andExpect(status().isPreconditionRequired());
		MvcResult first = patchOffer(offer, key, "\"rev-0\"", rename)
				.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\"")).andReturn();
		patchOffer(offer, UUID.randomUUID().toString(), "\"rev-1\"", "{\"basePrice\": 200}")
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-2\""));

		assertSameResponse(first, patchOffer(offer, key, "\"rev-0\"", rename).andExpect(status().isOk()).andReturn());
		mockMvc.perform(patch("/api/v1/menu/offers/" + offer).header(KEY, key)
				.contentType(MediaType.APPLICATION_JSON).content(rename))
				.andExpect(status().isPreconditionRequired());
		expectReused(patchOffer(offer, key, "\"rev-0\"", "{\"presentationTag\": \"Triple\"}"));
		expectReused(patchOffer(offer, key, "\"rev-2\"", rename));
		patchOffer(offer, UUID.randomUUID().toString(), "\"rev-0\"", rename)
				.andExpect(status().isPreconditionFailed());
	}

	@Test
	void imagesAreComparedByTheirBytesAndDeclaredType() throws Exception {
		byte[] png = png(30);
		String key = UUID.randomUUID().toString();
		MvcResult first = upload(key, "photo.png", MediaType.IMAGE_PNG_VALUE, png)
				.andExpect(status().isCreated()).andReturn();

		assertSameResponse(first, upload(key, "renamed.png", MediaType.IMAGE_PNG_VALUE, png)
				.andExpect(status().isCreated()).andReturn());
		expectReused(upload(key, "photo.png", MediaType.IMAGE_PNG_VALUE, png(31)));
		expectReused(upload(key, "photo.png", MediaType.IMAGE_JPEG_VALUE, png));

		MvcResult other = upload(UUID.randomUUID().toString(), "photo.png", MediaType.IMAGE_PNG_VALUE, png)
				.andExpect(status().isCreated()).andReturn();
		assertNotEquals(id(first), id(other));
		assertEquals(3, imageRepository.count());
	}

	@Test
	void aFailureOfInventoryCanBeRetriedWithTheSameKey() throws Exception {
		String key = UUID.randomUUID().toString();
		String recipe = recipe("150");
		INVENTORY.fail(503, "{}");
		createRecipe(key, recipe).andExpect(status().isBadGateway());
		assertEquals(0, recipeRepository.count());

		expectReused(createRecipe(key, recipe("200")));

		INVENTORY.reset();
		INVENTORY.add(BEEF);
		MvcResult created = createRecipe(key, recipe).andExpect(status().isCreated()).andReturn();
		assertSameResponse(created, createRecipe(key, recipe).andExpect(status().isCreated()).andReturn());
		assertEquals(1, recipeRepository.count());
	}

	@Test
	void aConcurrentRequestWithTheSameKeyDoesNotRunAgain() throws Exception {
		String key = UUID.randomUUID().toString();
		INVENTORY.answerSlowly();
		CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> {
			try {
				return createRecipe(key, recipe("150")).andReturn().getResponse().getStatus();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		while (INVENTORY.requests().isEmpty()) {
			Thread.onSpinWait();
		}

		createRecipe(key, recipe("150"))
				.andExpect(status().isConflict())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
				.andExpect(jsonPath(CODE).value("IDEMPOTENCY_IN_PROGRESS"));
		INVENTORY.reset();
		first.join();
	}

	@Test
	void corsAllowsTheKeyAndExposesRetryAfter() throws Exception {
		mockMvc.perform(options(CATEGORIES).header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "idempotency-key"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "idempotency-key"));
		mockMvc.perform(post(CATEGORIES).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "ETag, Location, Retry-After"));
	}

	private static void assertSameResponse(MvcResult expected, MvcResult actual) throws Exception {
		assertEquals(expected.getResponse().getStatus(), actual.getResponse().getStatus());
		assertEquals(expected.getResponse().getContentAsString(), actual.getResponse().getContentAsString());
		assertEquals(expected.getResponse().getHeader(HttpHeaders.ETAG), actual.getResponse().getHeader(HttpHeaders.ETAG));
	}

	private static void expectReused(ResultActions result) throws Exception {
		result.andExpect(status().isConflict()).andExpect(jsonPath(CODE).value("IDEMPOTENCY_KEY_REUSED"))
				.andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
	}

	private static String id(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private ResultActions createCategory(String key, String json) throws Exception {
		return mockMvc.perform(post(CATEGORIES).header(KEY, key).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions createEntry(String key, UUID... categories) throws Exception {
		return mockMvc.perform(post("/api/v1/menu/entries").header(KEY, key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"brandName\": \"Burger\", \"description\": \"Tasty\", \"imageId\": \"" + imageId
						+ "\", \"categoryIds\": [\"" + categories[0] + "\", \"" + categories[1] + "\"]}"));
	}

	private ResultActions createRecipe(String key, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/menu/recipes").header(KEY, key)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions patchOffer(String offer, String key, String ifMatch, String json) throws Exception {
		return mockMvc.perform(patch("/api/v1/menu/offers/" + offer).header(KEY, key)
				.header(HttpHeaders.IF_MATCH, ifMatch).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions upload(String key, String filename, String type, byte[] content) throws Exception {
		MockPart file = new MockPart("file", filename, content, MediaType.parseMediaType(type));
		return mockMvc.perform(multipart("/api/v1/media/images").part(file).header(KEY, key));
	}

	private UUID entry() {
		return entryRepository.save(new CatalogEntry(1L, "Hawaiian burger", "Tasty", imageId, List.of())).getId();
	}

	private String offerBody() {
		return """
				{"presentationTag": "Individual", "basePrice": 180, "imageId": "%s", "composition": {"slots": [
				  {"name": "Main", "quantity": 1, "options": [{"displayName": "Beef", "sourceType": "INVENTORY_ITEM",
				  "inventoryItemId": "%s", "quantity": 150, "unit": "g"}]}]}}
				""".formatted(imageId, BEEF.id());
	}

	private static String recipe(String quantity) {
		return "{\"name\": \"Burger\", \"description\": \"Classic\", \"instructions\": \"Grill and serve\", "
				+ "\"supplies\": [{\"inventoryItemId\": \"" + BEEF.id() + "\", \"quantity\": " + quantity + "}]}";
	}

	private static byte[] png(int size) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
