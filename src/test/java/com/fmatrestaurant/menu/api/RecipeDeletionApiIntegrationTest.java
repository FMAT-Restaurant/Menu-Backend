package com.fmatrestaurant.menu.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.RecipeService;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.ComponentIngredient;
import com.fmatrestaurant.menu.domain.CompositionSlot;
import com.fmatrestaurant.menu.domain.EntryStatus;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.domain.Recipe;
import com.fmatrestaurant.menu.domain.RecipeRevision;
import com.fmatrestaurant.menu.domain.SlotOption;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;

/** HTTP contract of REQ-MENU-REC-005 and BR-MENU-031 with PostgreSQL persistence. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class RecipeDeletionApiIntegrationTest {

	private static final String RECIPES = "/api/v1/menu/recipes";

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final InventoryItem BEEF = new InventoryItem("INV-beef-001", "Beef", "unit");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeRevisionRepository revisionRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RecipeService recipeService;

	private UUID recipeId;

	private UUID imageId;

	private CatalogEntry entry;

	@BeforeEach
	void setUp() throws IOException {
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		revisionRepository.deleteAll();
		recipeRepository.deleteAll();
		imageRepository.deleteAll();
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
			imageId = imageRepository.saveAndFlush(new Image("image/png", output.toByteArray())).getId();
		}
		entry = entryRepository.saveAndFlush(new CatalogEntry(1L, "Burger", "Classic", imageId, List.of()));
		recipeId = newRecipe("Burger");
		recipeService.update(recipeId, 0, "Better burger", null, null, null);
	}

	@Test
	void deletesOnlyTheCurrentDefinitionAndPreservesEveryPublishedRevisionAndIngredient() throws Exception {
		UUID otherRecipeId = newRecipe("Salad");
		String listEtag = mockMvc.perform(get(RECIPES)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		assertNotNull(listEtag, "The recipe list ETag should not be null");
		RecipeRevision first = recipeService.getRevision(recipeId, 1);
		RecipeRevision second = recipeService.getRevision(recipeId, 2);

		deleteRecipe("\"rev-1\"").andExpect(status().isNoContent())
				.andExpect(content().string(""))
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-2\""));

		mockMvc.perform(get(RECIPES + "/" + recipeId)).andExpect(status().isNotFound());
		mockMvc.perform(get(RECIPES).header(HttpHeaders.IF_NONE_MATCH, listEtag)).andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.total").value(1))
				.andExpect(jsonPath("$.data[0].id").value(otherRecipeId.toString()));
		mockMvc.perform(get(RECIPES).param("q", "burger")).andExpect(jsonPath("$.meta.total").value(0));
		assertTrue(recipeRepository.findById(recipeId).orElseThrow().isDeleted());
		assertEquals(3, revisionRepository.count());
		assertHistory(first, recipeService.getRevision(recipeId, 1));
		assertHistory(second, recipeService.getRevision(recipeId, 2));
		deleteRecipe("\"rev-2\"").andExpect(status().isNotFound());
		mockMvc.perform(patch(RECIPES + "/" + recipeId).header(HttpHeaders.IF_MATCH, "\"rev-2\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Restored\"}"))
				.andExpect(status().isNotFound());
		assertEquals(3, revisionRepository.count());
	}

	@ParameterizedTest
	@CsvSource({ "false,ACTIVE", "false,INACTIVE", "true,ACTIVE", "true,INACTIVE" })
	void rejectsReferencesToAnOlderRevisionRegardlessOfOfferAndOptionStatus(boolean enabled, OfferStatus status)
			throws Exception {
		CatalogOffer offer = referencedOffer(recipeId, enabled, status);
		String offerBefore = offerJson(offer.getId());
		String offerEtag = offerEtag(offer.getId());
		String recipeBefore = mockMvc.perform(get(RECIPES + "/" + recipeId))
				.andReturn().getResponse().getContentAsString();

		deleteRecipe("\"rev-1\"").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.details.offerIds", containsInAnyOrder(offer.getId().toString())));

		assertEquals(offerBefore, offerJson(offer.getId()));
		assertEquals(offerEtag, offerEtag(offer.getId()));
		mockMvc.perform(get(RECIPES + "/" + recipeId)).andExpect(content().json(recipeBefore))
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\""));
		assertFalse(recipeRepository.findById(recipeId).orElseThrow().isDeleted());
		assertEquals(2, revisionRepository.count());
	}

	@Test
	void reportsEveryReferencingOfferExactlyOnceEvenForArchivedEntriesAndRepeatedOptions() throws Exception {
		entry.update(null, null, null, null, EntryStatus.ARCHIVED);
		entry = entryRepository.saveAndFlush(entry);
		CatalogOffer first = referencedOffer(recipeId, false, OfferStatus.INACTIVE);
		CatalogOffer second = referencedOffer(recipeId, true, OfferStatus.ACTIVE);
		referencedOffer(newRecipe("Unrelated"), false, OfferStatus.ACTIVE);

		deleteRecipe("\"rev-1\"").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.details.offerIds",
						containsInAnyOrder(first.getId().toString(), second.getId().toString())));
		assertFalse(recipeRepository.findById(recipeId).orElseThrow().isDeleted());
	}

	@Test
	void deletingAnOfferRemovesItsReferencesSoTheRecipeCanBeDeleted() throws Exception {
		CatalogOffer offer = referencedOffer(recipeId, false, OfferStatus.INACTIVE);
		mockMvc.perform(delete(OFFERS + "/" + offer.getId()).header(HttpHeaders.IF_MATCH, "\"rev-0\""))
				.andExpect(status().isNoContent());

		deleteRecipe("\"rev-1\"").andExpect(status().isNoContent());
		assertEquals(2, revisionRepository.count());
	}

	@Test
	void rejectsMissingStaleWeakAndMalformedValidatorsWithoutDeleting() throws Exception {
		mockMvc.perform(delete(RECIPES + "/" + recipeId)).andExpect(status().isPreconditionRequired());
		for (String validator : List.of("\"rev-0\"", "\"rev-99\"", "W/\"rev-1\"", "invalid", "*")) {
			deleteRecipe(validator).andExpect(status().isPreconditionFailed());
		}
		assertFalse(recipeRepository.findById(recipeId).orElseThrow().isDeleted());
		assertEquals(1, recipeRepository.findById(recipeId).orElseThrow().getVersion());
		assertEquals(2, revisionRepository.count());
		mockMvc.perform(delete(RECIPES + "/" + UUID.randomUUID()).header(HttpHeaders.IF_MATCH, "\"rev-0\""))
				.andExpect(status().isNotFound());
	}

	@Test
	void cannotCreateOrReassignAnOptionToADeletedRecipeEvenThoughTheRevisionStillExists() throws Exception {
		deleteRecipe("\"rev-1\"").andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/menu/entries/" + entry.getId() + "/offers")
				.contentType(MediaType.APPLICATION_JSON).content(offerBody())).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.details.violations[0].path")
						.value("/composition/slots/0/options/0/recipeId"));
		assertEquals(0, offerRepository.count());

		CatalogOffer offer = referencedOffer(newRecipe("Salad"), false, OfferStatus.ACTIVE);
		CompositionSlot slot = offer.getSlots().getFirst();
		SlotOption option = slot.getOptions().getFirst();
		String before = offerJson(offer.getId());
		String patchBody = "{\"composition\":{\"slots\":[{\"slotId\":\"" + slot.getId()
				+ "\",\"options\":[{\"optionId\":\"" + option.getId() + "\",\"sourceType\":\"RECIPE\","
				+ "\"recipeId\":\"" + recipeId + "\",\"recipeRevision\":1}]}]}}";
		mockMvc.perform(patch(OFFERS + "/" + offer.getId()).header(HttpHeaders.IF_MATCH, offerEtag(offer.getId()))
				.contentType(MediaType.APPLICATION_JSON).content(patchBody))
				.andExpect(status().isUnprocessableContent());
		assertEquals(before, offerJson(offer.getId()));
	}

	@Test
	void removingTheRecipeOptionsThroughPatchAllowsDeletionAndKeepsTheOffer() throws Exception {
		CatalogOffer offer = referencedOffer(recipeId, false, OfferStatus.INACTIVE);
		CompositionSlot slot = offer.getSlots().getFirst();
		SlotOption inventoryOption = slot.getOptions().get(2);
		deleteRecipe("\"rev-1\"").andExpect(status().isConflict());

		String patchBody = """
				{"composition":{"slots":[{"slotId":"%s","options":[
				  {"optionId":"%s","sourceType":"INVENTORY_ITEM"}]}]}}
				""".formatted(slot.getId(), inventoryOption.getId());
		mockMvc.perform(patch(OFFERS + "/" + offer.getId()).header(HttpHeaders.IF_MATCH, offerEtag(offer.getId()))
				.contentType(MediaType.APPLICATION_JSON).content(patchBody)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].options.length()").value(1))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].sourceType").value("INVENTORY_ITEM"));
		String offerAfterPatch = offerJson(offer.getId());

		deleteRecipe("\"rev-1\"").andExpect(status().isNoContent());

		assertEquals(offerAfterPatch, offerJson(offer.getId()));
		mockMvc.perform(get(RECIPES + "/" + recipeId)).andExpect(status().isNotFound());
		assertEquals(2, revisionRepository.count());
	}

	@Test
	void editingThroughPatchInvalidatesTheEtagPreviouslyReadForDeletion() throws Exception {
		String oldEtag = mockMvc.perform(get(RECIPES + "/" + recipeId)).andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		assertNotNull(oldEtag);
		String currentEtag = mockMvc.perform(patch(RECIPES + "/" + recipeId).header(HttpHeaders.IF_MATCH, oldEtag)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Edited burger\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(3))
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		assertNotNull(currentEtag);

		deleteRecipe(oldEtag).andExpect(status().isPreconditionFailed());

		mockMvc.perform(get(RECIPES + "/" + recipeId)).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, currentEtag))
				.andExpect(jsonPath("$.data.name").value("Edited burger"))
				.andExpect(jsonPath("$.data.revision").value(3));
		deleteRecipe(currentEtag).andExpect(status().isNoContent());
		assertEquals(3, revisionRepository.count());
	}

	private String offerBody() {
		return "{\"presentationTag\":\"Individual\",\"basePrice\":10,\"imageId\":\"" + imageId
				+ "\",\"composition\":{\"slots\":[{\"name\":\"Main\",\"quantity\":1,\"options\":["
				+ "{\"displayName\":\"Burger\",\"sourceType\":\"RECIPE\",\"recipeId\":\""
				+ recipeId + "\",\"recipeRevision\":1}]}]}}";
	}

	private UUID newRecipe(String name) {
		Recipe recipe = recipeRepository.saveAndFlush(new Recipe());
		revisionRepository.saveAndFlush(new RecipeRevision(recipe, 1, name, "Classic", "Grill and serve",
				List.of(new ComponentIngredient(BEEF, BigDecimal.TWO))));
		return recipe.getId();
	}

	private CatalogOffer referencedOffer(UUID referencedRecipeId, boolean enabled, OfferStatus status) {
		List<SlotOption> options = List.of(
				new SlotOption("Recipe", status, referencedRecipeId, 1),
				new SlotOption("Same recipe", status, referencedRecipeId, 1),
				new SlotOption("Inventory", OfferStatus.ACTIVE, BEEF, BigDecimal.ONE, BEEF.unit()));
		CatalogOffer offer = new CatalogOffer(entry, "Individual", BigDecimal.TEN, imageId,
				List.of(new CompositionSlot("Main", BigDecimal.ONE, null, options)));
		if (enabled) {
			offer.changeStatus(OfferStatus.ACTIVE);
		}
		return offerRepository.saveAndFlush(offer);
	}

	private ResultActions deleteRecipe(String etag) throws Exception {
		return mockMvc.perform(delete(RECIPES + "/" + recipeId).header(HttpHeaders.IF_MATCH, etag));
	}

	private String offerJson(UUID id) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + id)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String offerEtag(UUID id) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + id)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static void assertHistory(RecipeRevision expected, RecipeRevision actual) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getRevision(), actual.getRevision());
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getDescription(), actual.getDescription());
		assertEquals(expected.getInstructions(), actual.getInstructions());
		assertEquals(expected.getIngredients().getFirst().getId(), actual.getIngredients().getFirst().getId());
		assertEquals(BEEF.id(), actual.getIngredients().getFirst().getInventoryItemId());
		assertEquals(BEEF.name(), actual.getIngredients().getFirst().getItemName());
		assertEquals(BEEF.unit(), actual.getIngredients().getFirst().getUnit());
		assertEquals(0, BigDecimal.TWO.compareTo(actual.getIngredients().getFirst().getQuantity()));
	}

}
