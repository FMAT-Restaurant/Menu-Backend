package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.RecipeRevision;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * HTTP flows of the recipe library against PostgreSQL (Testcontainers). Inventory articles are confirmed
 * by the real client against {@link InventoryStub}. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class RecipeApiIntegrationTest {

	private static final String RECIPES = "/api/v1/menu/recipes";

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	private static final UUID BEEF = UUID.randomUUID();

	private static final UUID BUN = UUID.randomUUID();

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
		createRecipe(recipe("Burger", supply(BEEF, "2")))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.id").exists())
				.andExpect(jsonPath("$.data.revision").value(1))
				.andExpect(jsonPath("$.data.name").value("Burger"))
				.andExpect(jsonPath("$.data.description").value("Classic"))
				.andExpect(jsonPath("$.data.instructions").value("Grill and serve"))
				.andExpect(jsonPath("$.data.supplies.length()").value(1))
				.andExpect(jsonPath("$.data.supplies[0].inventoryItemId").value(BEEF.toString()))
				.andExpect(jsonPath("$.data.supplies[0].quantity").value(2))
				.andExpect(jsonPath("$.data.supplies[0].inventoryItem.name").value("Beef patty"))
				.andExpect(jsonPath("$.data.supplies[0].inventoryItem.unit").value("unit"));

		assertEquals(1, recipeRepository.count());
		assertEquals(1, revisionRepository.count());
	}

	@Test
	void keepsTheOrderAndTheDecimalQuantitiesOfTheIngredients() throws Exception {
		createRecipe(recipe("Burger", supply(BUN, "1"), supply(BEEF, "150.5")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.supplies[0].inventoryItemId").value(BUN.toString()))
				.andExpect(jsonPath("$.data.supplies[1].inventoryItemId").value(BEEF.toString()))
				.andExpect(jsonPath("$.data.supplies[1].quantity").value(150.5));
	}

	@Test
	void rejectsInvalidRecipesAndStoresNothing() throws Exception {
		String valid = recipe("Burger", supply(BEEF, "2"));

		expectViolation(createRecipe(recipe("Burger")), "/supplies");
		expectViolation(createRecipe(valid.replace("\"name\": \"Burger\", ", "")), "/name");
		expectViolation(createRecipe(valid.replace("\"instructions\": \"Grill and serve\", ", "")), "/instructions");
		expectViolation(createRecipe(valid.replace("\"quantity\": 2", "\"quantity\": 0")), "/supplies/0/quantity");
		expectViolation(createRecipe(valid.replace("\"quantity\": 2", "\"quantity\": null")),
				"/supplies/0/quantity");
		expectViolation(createRecipe(valid.replace("\"inventoryItemId\": \"" + BEEF + "\", ", "")),
				"/supplies/0/inventoryItemId");
		expectViolation(createRecipe(recipe("Burger", supply(UUID.randomUUID(), "1"))),
				"/supplies/0/inventoryItemId");

		assertEquals(0, recipeRepository.count());
		assertEquals(0, revisionRepository.count());
	}

	@Test
	void answersBadGatewayAndStoresNothingWhenInventoryCannotConfirmTheArticles() throws Exception {
		INVENTORY.fail(503, "{}");

		createRecipe(recipe("Burger", supply(BEEF, "2"))).andExpect(status().isBadGateway());

		assertEquals(0, recipeRepository.count());
		assertEquals(0, revisionRepository.count());
	}

	@Test
	void editingAnArticleThatDoesNotExistPublishesNothing() throws Exception {
		String id = create(recipe("Burger", supply(BEEF, "2")));

		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"supplies\": [" + supply(UUID.randomUUID(), "1") + "]}"),
				"/supplies/0/inventoryItemId");

		assertEquals(1, revisionRepository.count());
	}

	@Test
	void rejectsABodyThatIsNotJson() throws Exception {
		createRecipe("not json").andExpect(status().isUnprocessableContent());
	}

	@Test
	void getsTheCurrentRevision() throws Exception {
		String id = create(recipe("Burger", supply(BEEF, "2")));

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
		create(recipe("Cheeseburger", supply(BEEF, "1")));
		create(recipe("Salad", supply(BUN, "50")));
		create(recipe("Burger", supply(BEEF, "2")));

		mockMvc.perform(get(RECIPES))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(3))
				.andExpect(jsonPath("$.data[0].name").value("Burger"))
				.andExpect(jsonPath("$.data[1].name").value("Cheeseburger"))
				.andExpect(jsonPath("$.data[2].name").value("Salad"))
				.andExpect(jsonPath("$.data[0].revision").value(1))
				.andExpect(jsonPath("$.data[0].instructions").doesNotExist())
				.andExpect(jsonPath("$.data[0].supplies[0].inventoryItem.unit").value("unit"))
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
		String id = create(recipe("Burger", supply(BEEF, "2")));

		patchRecipe(id, "\"rev-0\"", "{\"name\": \"Better burger\"}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\""))
				.andExpect(jsonPath("$.data.revision").value(2))
				.andExpect(jsonPath("$.data.name").value("Better burger"))
				.andExpect(jsonPath("$.data.description").value("Classic"))
				.andExpect(jsonPath("$.data.instructions").value("Grill and serve"))
				.andExpect(jsonPath("$.data.supplies.length()").value(1))
				.andExpect(jsonPath("$.data.supplies[0].inventoryItemId").value(BEEF.toString()));

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
		String id = create(recipe("Burger", supply(BEEF, "2")));

		patchRecipe(id, "\"rev-0\"", "{\"supplies\": [" + supply(BUN, "1") + "]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.revision").value(2))
				.andExpect(jsonPath("$.data.supplies.length()").value(1))
				.andExpect(jsonPath("$.data.supplies[0].inventoryItemId").value(BUN.toString()));

		RecipeRevision first = revisionRepository.findByRecipeIdAndRevision(UUID.fromString(id), 1).orElseThrow();
		assertEquals(BEEF, first.getIngredients().getFirst().getInventoryItemId());
	}

	@Test
	void editingWithoutChangesKeepsTheRevisionAndTheEtag() throws Exception {
		String id = create(recipe("Burger", supply(BEEF, "2")));

		patchRecipe(id, "\"rev-0\"", "{}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.revision").value(1));

		assertEquals(1, revisionRepository.count());
	}

	@Test
	void anInvalidEditPublishesNothing() throws Exception {
		String id = create(recipe("Burger", supply(BEEF, "2")));

		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"supplies\": []}"), "/supplies");
		expectViolation(patchRecipe(id, "\"rev-0\"", "{\"name\": \" \"}"), "/name");
		expectViolation(patchRecipe(id, "\"rev-0\"",
				"{\"name\": \"Changed\", \"supplies\": [" + supply(BUN, "-1") + "]}"),
				"/supplies/0/quantity");

		assertEquals(1, revisionRepository.count());
		mockMvc.perform(get(RECIPES + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.name").value("Burger"));
	}

	@Test
	void rejectsAMissingStaleWeakOrMalformedValidator() throws Exception {
		String id = create(recipe("Burger", supply(BEEF, "2")));

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
		String id = create(recipe("Burger", supply(BEEF, "2")));
		String listEtag = mockMvc.perform(get(RECIPES)).andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.ETAG)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

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

	private ResultActions createRecipe(String json) throws Exception {
		return mockMvc.perform(post(RECIPES).contentType(MediaType.APPLICATION_JSON).content(json));
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

	private static String recipe(String name, String... supplies) {
		return "{\"name\": \"" + name + "\", \"description\": \"Classic\", \"instructions\": \"Grill and serve\", "
				+ "\"supplies\": [" + String.join(", ", supplies) + "]}";
	}

	private static String supply(UUID item, String quantity) {
		return "{\"inventoryItemId\": \"" + item + "\", \"quantity\": " + quantity + "}";
	}

}
