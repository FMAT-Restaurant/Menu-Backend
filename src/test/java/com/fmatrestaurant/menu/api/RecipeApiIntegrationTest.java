package com.fmatrestaurant.menu.api;

import static com.fmatrestaurant.menu.api.ContractValidation.conformsToContract;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.TestSecurityConfiguration;
import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.fmatrestaurant.menu.domain.model.RecipeRevision;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * HTTP flows of the recipe library against PostgreSQL (Testcontainers). Inventory articles are confirmed
 * by the real client against {@link InventoryStub}. Requires Docker.
 */
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class RecipeApiIntegrationTest {

	private static final String RECIPES = "/api/v1/menu/recipes";

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	private static final String BEEF = "INV-beef-001";

	private static final String BUN = "000042";

	private static final InventoryStub INVENTORY = InventoryStub.INSTANCE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeRevisionRepository revisionRepository;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() {
		INVENTORY.reset();
		INVENTORY.add(new InventoryItem(BEEF, "Beef patty", "unit"));
		INVENTORY.add(new InventoryItem(BUN, "Bun", "unit"));
		revisionRepository.deleteAll();
		recipeRepository.deleteAll();
	}

	@Test
	void createsARecipeWithItsFirstRevision() throws Exception {
		createRecipe(recipe("Burger", ingredient(BEEF, "2")))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.id").exists())
				.andExpect(jsonPath("$.data.revision").value(1))
				.andExpect(jsonPath("$.data.name").value("Burger"))
				.andExpect(jsonPath("$.data.description").value("Classic"))
				.andExpect(jsonPath("$.data.instructions").value("Grill and serve"))
				.andExpect(jsonPath("$.data.ingredients.length()").value(1))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(BEEF))
				.andExpect(jsonPath("$.data.ingredients[0].quantity").value(2))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItem.name").value("Beef patty"))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItem.unit").value("unit"));

		assertEquals(1, recipeRepository.count());
		assertEquals(1, revisionRepository.count());
	}

	@Test
	void keepsTheOrderAndTheDecimalQuantitiesOfTheIngredients() throws Exception {
		createRecipe(recipe("Burger", ingredient(BUN, "1"), ingredient(BEEF, "150.5")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(BUN))
				.andExpect(jsonPath("$.data.ingredients[1].inventoryItemId").value(BEEF))
				.andExpect(jsonPath("$.data.ingredients[1].quantity").value(150.5));
	}

	@Test
	void preservesLongOpaqueIdsWhenReadingAndPublishingANewRevision() throws Exception {
		String item = "inventory:article/" + "x".repeat(300);
		INVENTORY.add(new InventoryItem(item, "Special ingredient", "unit"));
		String id = create(recipe("Burger", ingredient(item, "2")));

		mockMvc.perform(get(RECIPES + "/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(item))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItem.id").value(item));
		patchRecipe(id, "\"rev-0\"", "{\"name\": \"Updated burger\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(item));
	}

	@Test
	void rejectsInvalidRecipesAndStoresNothing() throws Exception {
		String valid = recipe("Burger", ingredient(BEEF, "2"));

		expectViolation(createRecipe(recipe("Burger", ingredient(" ", "1"))),
				"/ingredients/0/inventoryItemId");

		expectViolation(createRecipe(recipe("Burger")), "/ingredients");
		expectViolation(createRecipe(valid.replace("\"name\": \"Burger\", ", "")), "/name");
		expectViolation(createRecipe(valid.replace("\"instructions\": \"Grill and serve\", ", "")), "/instructions");
		expectViolation(createRecipe(valid.replace("\"quantity\": 2", "\"quantity\": 0")), "/ingredients/0/quantity");
		expectViolation(createRecipe(valid.replace("\"quantity\": 2", "\"quantity\": null")),
				"/ingredients/0/quantity");
		expectViolation(createRecipe(valid.replace("\"inventoryItemId\": \"" + BEEF + "\", ", "")),
				"/ingredients/0/inventoryItemId");
		expectViolation(createRecipe(recipe("Burger", ingredient("unknown-item", "1"))),
				"/ingredients/0/inventoryItemId");

		assertEquals(0, recipeRepository.count());
		assertEquals(0, revisionRepository.count());
	}

	@Test
	void answersBadGatewayAndStoresNothingWhenInventoryCannotConfirmTheArticles() throws Exception {
		INVENTORY.fail(503, "{}");

		createRecipe(recipe("Burger", ingredient(BEEF, "2"))).andExpect(status().isBadGateway());

		assertEquals(0, recipeRepository.count());
		assertEquals(0, revisionRepository.count());
	}

	/** A 404 is "the article does not exist" only with the error envelope of Inventory; otherwise it is a failure. */
	@Test
	void aNotFoundThatIsNotTheAnswerOfInventoryIsBadGatewayAndStoresNothing() throws Exception {
		String burger = recipe("Burger", ingredient(BEEF, "2"));
		for (String body : List.of("", "not json", "{\"error\": \"Not Found\"}")) {
			INVENTORY.fail(404, body);
			createRecipe(burger).andExpect(status().isBadGateway());
		}
		INVENTORY.reset();
		INVENTORY.fail(404, "{\"error\": {\"message\": \"No se encontró el recurso solicitado.\"}}");
		expectViolation(createRecipe(burger), "/ingredients/0/inventoryItemId");
		// The same 404 from a server that does not serve Inventory at the base URL confirms nothing.
		INVENTORY.failCollection(404, "{\"error\": {\"message\": \"The requested resource was not found.\"}}");
		createRecipe(burger).andExpect(status().isBadGateway());

		assertEquals(0, recipeRepository.count());
		assertEquals(0, revisionRepository.count());
	}

	@Test
	void editingAnArticleThatDoesNotExistPublishesNothing() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"ingredients\": [" + ingredient("unknown-item", "1") + "]}"),
				"/ingredients/0/inventoryItemId");

		assertEquals(1, revisionRepository.count());
	}

	@Test
	void rejectsABodyThatIsNotJson() throws Exception {
		createRecipe("not json").andExpect(status().isUnprocessableContent());
	}

	@Test
	void getsTheCurrentRevision() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		mockMvc.perform(get(RECIPES + "/" + id))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.id").value(id))
				.andExpect(jsonPath("$.data.revision").value(1))
				.andExpect(jsonPath("$.data.instructions").value("Grill and serve"));
	}

	@Test
	void answersNotFoundForAnUnknownOrMalformedRecipe() throws Exception {
		mockMvc.perform(get(RECIPES + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
		mockMvc.perform(get(RECIPES + "/not-a-uuid")).andExpect(status().isNotFound());
	}

	@Test
	void listsSearchesAndPaginatesTheCurrentRevisions() throws Exception {
		create(recipe("Cheeseburger", ingredient(BEEF, "1")));
		create(recipe("Salad", ingredient(BUN, "50")));
		create(recipe("Burger", ingredient(BEEF, "2")));

		mockMvc.perform(get(RECIPES))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(3))
				.andExpect(jsonPath("$.data[0].name").value("Burger"))
				.andExpect(jsonPath("$.data[1].name").value("Cheeseburger"))
				.andExpect(jsonPath("$.data[2].name").value("Salad"))
				.andExpect(jsonPath("$.data[0].revision").value(1))
				.andExpect(jsonPath("$.data[0].instructions").doesNotExist())
				.andExpect(jsonPath("$.data[0].ingredients[0].inventoryItem.unit").value("unit"))
				.andExpect(jsonPath("$.meta.page").value(1))
				.andExpect(jsonPath("$.meta.pageSize").value(12))
				.andExpect(jsonPath("$.meta.total").value(3))
				.andExpect(jsonPath("$.meta.totalPages").value(1));
		mockMvc.perform(get(RECIPES).param("q", "BURGER"))
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.meta.total").value(2));
		mockMvc.perform(get(RECIPES).param("q", "classic"))
				.andExpect(jsonPath("$.meta.total").value(3));
		mockMvc.perform(get(RECIPES).param("q", "%"))
				.andExpect(jsonPath("$.meta.total").value(0));
		mockMvc.perform(get(RECIPES).param("pageSize", "2").param("page", "2"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Salad"))
				.andExpect(jsonPath("$.meta.totalPages").value(2));
		expectViolation(mockMvc.perform(get(RECIPES).param("page", "0")), "page");
		expectViolation(mockMvc.perform(get(RECIPES).param("pageSize", "0")), "pageSize");
	}

	@Test
	void editingPublishesANewRevisionAndKeepsTheOldOnes() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		patchRecipe(id, "\"rev-0\"", "{\"name\": \"Better burger\"}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\""))
				.andExpect(jsonPath("$.data.revision").value(2))
				.andExpect(jsonPath("$.data.name").value("Better burger"))
				.andExpect(jsonPath("$.data.description").value("Classic"))
				.andExpect(jsonPath("$.data.instructions").value("Grill and serve"))
				.andExpect(jsonPath("$.data.ingredients.length()").value(1))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(BEEF));

		RecipeRevision first = revisionRepository.findByRecipeIdAndRevision(UUID.fromString(id), 1).orElseThrow();
		RecipeRevision second = revisionRepository.findByRecipeIdAndRevision(UUID.fromString(id), 2).orElseThrow();
		assertEquals("Burger", first.getName());
		assertEquals("Better burger", second.getName());
		assertEquals(1, first.getIngredients().size());
		assertEquals(2, revisionRepository.count());
		mockMvc.perform(get(RECIPES + "/" + id)).andExpect(jsonPath("$.data.revision").value(2));
		mockMvc.perform(get(RECIPES)).andExpect(jsonPath("$.meta.total").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Better burger"));
	}

	@Test
	void editingReplacesTheIngredientsWhenTheyAreSent() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		patchRecipe(id, "\"rev-0\"", "{\"ingredients\": [" + ingredient(BUN, "1") + "]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.revision").value(2))
				.andExpect(jsonPath("$.data.ingredients.length()").value(1))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(BUN));

		RecipeRevision first = revisionRepository.findByRecipeIdAndRevision(UUID.fromString(id), 1).orElseThrow();
		assertEquals(BEEF, first.getIngredients().getFirst().getInventoryItemId());
	}

	@Test
	void editingWithoutChangesKeepsTheRevisionAndTheEtag() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		patchRecipe(id, "\"rev-0\"", "{}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.revision").value(1));

		assertEquals(1, revisionRepository.count());
	}

	@Test
	void anInvalidEditPublishesNothing() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"ingredients\": []}"), "/ingredients");
		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"name\": \" \"}"), "/name");
		expectViolation(patchRecipe(id, "\"rev-0\"",
				"{\"name\": \"Changed\", \"ingredients\": [" + ingredient(BUN, "-1") + "]}"),
				"/ingredients/0/quantity");

		assertEquals(1, revisionRepository.count());
		mockMvc.perform(get(RECIPES + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.name").value("Burger"));
	}

	@Test
	void rejectsAMissingStaleWeakOrMalformedValidator() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		mockMvc.perform(patch(RECIPES + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Changed\"}")).andExpect(status().isPreconditionRequired());
		patchRecipe(id, "\"rev-99\"", "{\"name\": \"Changed\"}").andExpect(status().isPreconditionFailed());
		patchRecipe(id, "W/\"rev-0\"", "{\"name\": \"Changed\"}").andExpect(status().isPreconditionFailed());
		patchRecipe(id, "not an etag", "{\"name\": \"Changed\"}").andExpect(status().isPreconditionFailed());

		patchRecipe(id, "\"rev-0\"", "{\"name\": \"Changed\"}").andExpect(status().isOk());
		patchRecipe(id, "\"rev-0\"", "{\"name\": \"Overwritten\"}").andExpect(status().isPreconditionFailed());

		mockMvc.perform(get(RECIPES + "/" + id)).andExpect(jsonPath("$.data.name").value("Changed"));
		assertEquals(2, revisionRepository.count());
	}

	@Test
	void editingAnUnknownRecipeAnswersNotFound() throws Exception {
		patchRecipe(UUID.randomUUID().toString(), "\"rev-0\"", "{\"name\": \"Changed\"}")
				.andExpect(status().isNotFound());
	}

	@Test
	void answersNotModifiedWhileTheValidatorOfTheRecipeOrOfTheListStillMatches() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));
		String listEtag = mockMvc.perform(get(RECIPES)).andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.ETAG)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		assertNotNull(listEtag, "ETag header is missing from GET response");
		mockMvc.perform(get(RECIPES + "/" + id).header(HttpHeaders.IF_NONE_MATCH, "\"rev-0\""))
				.andExpect(status().isNotModified());
		mockMvc.perform(get(RECIPES).header(HttpHeaders.IF_NONE_MATCH, listEtag))
				.andExpect(status().isNotModified());

		patchRecipe(id, "\"rev-0\"", "{\"name\": \"Better burger\"}").andExpect(status().isOk());

		mockMvc.perform(get(RECIPES + "/" + id).header(HttpHeaders.IF_NONE_MATCH, "\"rev-0\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.revision").value(2));
		mockMvc.perform(get(RECIPES).header(HttpHeaders.IF_NONE_MATCH, listEtag))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("TC-REC-02-CA8: the ingredient lines keep their order and conform to the contract")
	void createKeepsIngredientOrderAndConformsToContract() throws Exception {
		String id = JsonPath.read(createRecipe(recipe("Burger", ingredient(BUN, "1"), ingredient(BEEF, "150")))
				.andExpect(status().isCreated())
				.andExpect(conformsToContract())
				.andExpect(jsonPath("$.data.revision").value(1))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItemId").value(BUN))
				.andExpect(jsonPath("$.data.ingredients[1].inventoryItemId").value(BEEF))
				.andReturn().getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get(RECIPES + "/" + id)).andExpect(status().isOk()).andExpect(conformsToContract());
		mockMvc.perform(get(RECIPES)).andExpect(status().isOk()).andExpect(conformsToContract());
	}

	@Test
	@DisplayName("TC-REC-03-CA1: PATCH replaces the ingredient lines and shows the unit of Inventory")
	void patchReplacesIngredients() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "150"), ingredient(BUN, "1")));

		patchRecipe(id, "\"rev-0\"", "{\"ingredients\": [" + ingredient(BEEF, "180") + "]}")
				.andExpect(status().isOk())
				.andExpect(conformsToContract())
				.andExpect(jsonPath("$.data.revision").value(2))
				.andExpect(jsonPath("$.data.ingredients.length()").value(1))
				.andExpect(jsonPath("$.data.ingredients[0].quantity").value(180))
				.andExpect(jsonPath("$.data.ingredients[0].inventoryItem.unit").value("unit"));
	}

	@Test
	@DisplayName("TC-REC-04-CA2: PATCH validates the ingredient lines as POST does")
	void patchValidatesIngredients() throws Exception {
		String id = create(recipe("Burger", ingredient(BEEF, "2")));

		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"ingredients\": []}"), "/ingredients");
		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"ingredients\": [" + ingredient(BEEF, "0") + "]}"),
				"/ingredients/0/quantity");
		expectViolation(patchRecipe(id, "\"rev-0\"",
				"{\"ingredients\": [" + ingredient(BEEF, "1") + ", " + ingredient(BEEF, "2") + "]}"),
				"/ingredients/1/inventoryItemId");
		mockMvc.perform(get(RECIPES + "/" + id)).andExpect(jsonPath("$.data.revision").value(1));
	}

	@Test
	@DisplayName("TC-REC-02-CA5: an article repeated in two lines is rejected")
	void createRejectsRepeatedArticle() throws Exception {
		expectViolation(createRecipe(recipe("Burger", ingredient(BEEF, "150"), ingredient(BEEF, "50"))),
				"/ingredients/1/inventoryItemId");
		assertEquals(0, recipeRepository.count());
	}

	@Test
	@DisplayName("TC-REC-02-CA4a: a field outside the contract, such as a unit of the line, is rejected")
	void createRejectsUnknownFields() throws Exception {
		expectViolation(createRecipe(recipe("Burger",
				"{\"inventoryItemId\": \"" + BEEF + "\", \"quantity\": 1, \"unit\": \"g\"}")), "/ingredients/0/unit");
		expectViolation(createRecipe("{\"name\": \"Burger\", \"description\": \"Classic\", \"instructions\": \"Grill\", "
				+ "\"supplies\": [" + ingredient(BEEF, "1") + "]}"), "/supplies");
	}

	private ResultActions createRecipe(String json) throws Exception {
		return mockMvc.perform(post(RECIPES).header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String create(String json) throws Exception {
		return JsonPath.read(createRecipe(json).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.data.id");
	}

	private ResultActions patchRecipe(String id, String ifMatch, String json) throws Exception {
		return mockMvc.perform(patch(RECIPES + "/" + id).header(HttpHeaders.IF_MATCH, ifMatch)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static void expectViolation(ResultActions result, String path) throws Exception {
		result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath(VIOLATION_PATH).value(path));
	}

	private static String recipe(String name, String... lines) {
		return "{\"name\": \"" + name + "\", \"description\": \"Classic\", \"instructions\": \"Grill and serve\", "
				+ "\"ingredients\": [" + String.join(", ", lines) + "]}";
	}

	private static String ingredient(String item, String quantity) {
		return "{\"inventoryItemId\": \"" + item + "\", \"quantity\": " + quantity + "}";
	}

}
