package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

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
import com.fmatrestaurant.menu.application.RecipeService;
import com.fmatrestaurant.menu.application.RecipeService.SupplyData;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Offers whose options have recipe content (REQ-MENU-CONT-001, REQ-MENU-CONT-003) against PostgreSQL
 * (Testcontainers). Inventory articles are confirmed by the real client against {@link InventoryStub}.
 * Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class CatalogOfferRecipeApiIntegrationTest {

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final String OPTION = "$.data.composition.slots[0].options[0]";

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	private static final InventoryItem BEEF = new InventoryItem(UUID.randomUUID(), "Beef patty", "unit");

	private static final InventoryItem POTATOES = new InventoryItem(UUID.randomUUID(), "Potatoes", "g");

	private static final InventoryStub INVENTORY = InventoryStub.INSTANCE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeRevisionRepository revisionRepository;

	@Autowired
	private RecipeService recipeService;

	private UUID imageId;

	private UUID entryId;

	private UUID recipeId;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws IOException {
		INVENTORY.reset();
		INVENTORY.add(BEEF);
		INVENTORY.add(POTATOES);
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		revisionRepository.deleteAll();
		recipeRepository.deleteAll();
		imageRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png())).getId();
		entryId = newEntry();
		recipeId = recipeService.create("Burger", "Classic", "Grill and serve",
				List.of(new SupplyData(BEEF.id(), BigDecimal.TWO)))
				.getRecipe().getId();
		recipeService.update(recipeId, 0, "Better burger", null, null, null);
	}

	@Test
	void createsAnOfferWhoseOptionPointsToARecipeRevision() throws Exception {
		createOffer(entryId, offerBody(recipeId, 1))
				.andExpect(status().isCreated())
				.andExpect(jsonPath(OPTION + ".sourceType").value("RECIPE"))
				.andExpect(jsonPath(OPTION + ".recipeId").value(recipeId.toString()))
				.andExpect(jsonPath(OPTION + ".recipeRevision").value(1))
				.andExpect(jsonPath(OPTION + ".displayName").value("House burger"))
				.andExpect(jsonPath(OPTION + ".status").value("ACTIVE"))
				.andExpect(jsonPath(OPTION + ".inventoryItemId").doesNotExist())
				.andExpect(jsonPath(OPTION + ".quantity").doesNotExist())
				.andExpect(jsonPath(OPTION + ".unit").doesNotExist());
	}

	@Test
	void createsAnOfferThatMixesInventoryAndRecipeOptions() throws Exception {
		String inventoryOption = "{\"displayName\": \"Fries\", \"sourceType\": \"INVENTORY_ITEM\", \"inventoryItemId\": \""
				+ POTATOES.id() + "\", \"quantity\": 200, \"unit\": \"g\"}";
		String recipeOption = "{\"displayName\": \"House burger\", \"sourceType\": \"RECIPE\", \"recipeId\": \""
				+ recipeId + "\", \"recipeRevision\": 2}";
		String body = "{\"presentationTag\": \"Combo\", \"basePrice\": 220, \"imageId\": \"" + imageId
				+ "\", \"composition\": {\"slots\": [{\"name\": \"Main\", \"quantity\": 1, \"options\": ["
				+ inventoryOption + ", " + recipeOption + "]}]}}";

		createOffer(entryId, body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].sourceType").value("INVENTORY_ITEM"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].inventoryItemId").value(POTATOES.id().toString()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].recipeId").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].recipeRevision").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options[1].sourceType").value("RECIPE"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[1].recipeRevision").value(2))
				.andExpect(jsonPath("$.data.composition.slots[0].options[1].inventoryItemId").doesNotExist());
	}

	@Test
	void theOptionKeepsItsRevisionWhenTheRecipeIsEditedAgain() throws Exception {
		String offer = create(entryId, offerBody(recipeId, 1));

		recipeService.update(recipeId, 1, "Best burger", null, null, null);

		mockMvc.perform(get(OFFERS + "/" + offer))
				.andExpect(jsonPath(OPTION + ".recipeRevision").value(1));
		assertEquals(3, revisionRepository.count());
	}

	@Test
	void aRecipeCanBeReusedWithADifferentRevisionInEachOffer() throws Exception {
		UUID otherEntry = newEntry();

		String first = create(entryId, offerBody(recipeId, 1));
		String second = create(otherEntry, offerBody(recipeId, 2));

		mockMvc.perform(get(OFFERS + "/" + first)).andExpect(jsonPath(OPTION + ".recipeRevision").value(1));
		mockMvc.perform(get(OFFERS + "/" + second)).andExpect(jsonPath(OPTION + ".recipeRevision").value(2));
		assertEquals(2, revisionRepository.count());
	}

	@Test
	void rejectsARecipeOrRevisionThatDoesNotExistWithoutCreatingTheOffer() throws Exception {
		expectViolation(createOffer(entryId, offerBody(recipeId, 3)),
				"/composition/slots/0/options/0/recipeRevision");
		expectViolation(createOffer(entryId, offerBody(UUID.randomUUID(), 1)),
				"/composition/slots/0/options/0/recipeRevision");
		expectViolation(createOffer(entryId, offerBody(recipeId, 1).replace("\"recipeId\": \"" + recipeId + "\", ", "")),
				"/composition/slots/0/options/0/recipeId");
		expectViolation(createOffer(entryId, offerBody(recipeId, 1).replace(", \"recipeRevision\": 1", "")),
				"/composition/slots/0/options/0/recipeRevision");
		expectViolation(createOffer(entryId, offerBody(recipeId, 0)),
				"/composition/slots/0/options/0/recipeRevision");

		assertEquals(0, offerRepository.count());
	}

	@Test
	void assigningARecipeDoesNotChangeIt() throws Exception {
		create(entryId, offerBody(recipeId, 1));

		assertEquals(2, revisionRepository.count());
		assertEquals(2, recipeService.get(recipeId).getRevision());
		assertEquals("Burger", recipeService.getRevision(recipeId, 1).getName());
	}

	@Test
	void anOptionIsReassignedToAnotherRevisionOnlyWhenAdministratorsAskForIt() throws Exception {
		String offer = create(entryId, offerBody(recipeId, 1));

		patchOffer(offer, optionPatch(offer, "\"recipeId\": \"" + recipeId + "\", \"recipeRevision\": 2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(OPTION + ".recipeId").value(recipeId.toString()))
				.andExpect(jsonPath(OPTION + ".recipeRevision").value(2));

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(jsonPath(OPTION + ".recipeRevision").value(2));
	}

	@Test
	void anOptionCanChangeItsNameAndStatusWithoutChangingItsRecipe() throws Exception {
		String offer = create(entryId, offerBody(recipeId, 1));

		patchOffer(offer, optionPatch(offer, "\"displayName\": \"Burger\", \"status\": \"INACTIVE\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath(OPTION + ".displayName").value("Burger"))
				.andExpect(jsonPath(OPTION + ".status").value("INACTIVE"))
				.andExpect(jsonPath(OPTION + ".recipeRevision").value(1));
	}

	@Test
	void anInvalidAssignmentLeavesTheOptionAndTheOfferUnchanged() throws Exception {
		String offer = create(entryId, offerBody(recipeId, 1));
		String etag = etag(offer);

		expectViolation(patchOffer(offer, optionPatch(offer, "\"recipeId\": \"" + recipeId + "\", \"recipeRevision\": 99")),
				"/composition/slots/0/options/0/recipeRevision");
		expectViolation(patchOffer(offer, optionPatch(offer, "\"recipeRevision\": 2")),
				"/composition/slots/0/options/0/recipeId");
		expectViolation(patchOffer(offer, optionPatch(offer, "\"recipeId\": \"" + recipeId + "\"")),
				"/composition/slots/0/options/0/recipeRevision");

		mockMvc.perform(get(OFFERS + "/" + offer))
				.andExpect(header().string(HttpHeaders.ETAG, etag))
				.andExpect(jsonPath(OPTION + ".recipeRevision").value(1));
	}

	@Test
	void aRecipeOptionRejectsInventoryContentAndAChangeOfSourceType() throws Exception {
		String offer = create(entryId, offerBody(recipeId, 1));

		expectViolation(patchOffer(offer, optionPatch(offer, "\"quantity\": 2")),
				"/composition/slots/0/options/0/quantity");
		expectViolation(patchOffer(offer, optionPatch(offer, "\"unit\": \"g\"")),
				"/composition/slots/0/options/0/unit");
		expectViolation(patchOffer(offer, optionPatch(offer,
				"\"inventoryItemId\": \"" + BEEF.id() + "\"")),
				"/composition/slots/0/options/0/inventoryItemId");
		expectViolation(patchOffer(offer, optionPatch(offer, "INVENTORY_ITEM", "")),
				"/composition/slots/0/options/0/sourceType");

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(jsonPath(OPTION + ".sourceType").value("RECIPE"));
	}

	@Test
	void anOptionWithStaleValidatorIsNotReassigned() throws Exception {
		String offer = create(entryId, offerBody(recipeId, 1));

		mockMvc.perform(patch(OFFERS + "/" + offer).header(HttpHeaders.IF_MATCH, "\"rev-99\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content(optionPatch(offer, "\"recipeId\": \"" + recipeId + "\", \"recipeRevision\": 2")))
				.andExpect(status().isPreconditionFailed());

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(jsonPath(OPTION + ".recipeRevision").value(1));
	}

	private UUID newEntry() {
		return entryRepository.save(new CatalogEntry(1L, "Hawaiian burger", "Tasty", imageId, List.of())).getId();
	}

	private ResultActions createOffer(UUID entry, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/menu/entries/" + entry + "/offers")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String create(UUID entry, String json) throws Exception {
		return JsonPath.read(createOffer(entry, json).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.data.id");
	}

	private String optionPatch(String offer, String optionFields) throws Exception {
		return optionPatch(offer, "RECIPE", optionFields);
	}

	/** Changes the option of the only slot of the offer; the source type is always sent, as the contract requires. */
	private String optionPatch(String offer, String sourceType, String optionFields) throws Exception {
		String body = mockMvc.perform(get(OFFERS + "/" + offer)).andReturn().getResponse().getContentAsString();
		String slot = JsonPath.read(body, "$.data.composition.slots[0].slotId");
		String option = JsonPath.read(body, "$.data.composition.slots[0].options[0].optionId");
		return "{\"composition\": {\"slots\": [{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \""
				+ option + "\", \"sourceType\": \"" + sourceType + "\"" + (optionFields.isEmpty() ? "" : ", " + optionFields)
				+ "}]}]}}";
	}

	private ResultActions patchOffer(String offer, String json) throws Exception {
		return mockMvc.perform(patch(OFFERS + "/" + offer).header(HttpHeaders.IF_MATCH, etag(offer))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String etag(String offer) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + offer)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static void expectViolation(ResultActions result, String path) throws Exception {
		result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath(VIOLATION_PATH).value(path));
	}

	private String offerBody(UUID recipe, int revision) {
		return "{\"presentationTag\": \"Individual\", \"basePrice\": 180, \"imageId\": \"" + imageId
				+ "\", \"composition\": {\"slots\": [{\"name\": \"Main\", \"quantity\": 1, \"options\": [{"
				+ "\"displayName\": \"House burger\", \"sourceType\": \"RECIPE\", "
				+ "\"recipeId\": \"" + recipe + "\", \"recipeRevision\": " + revision + "}]}]}}";
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
