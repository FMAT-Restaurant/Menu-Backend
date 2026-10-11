package com.fmatrestaurant.menu.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CatalogOfferService;
import com.fmatrestaurant.menu.application.CatalogOfferService.OfferInput;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.InventoryClient.InventoryItemPage;
import com.fmatrestaurant.menu.infrastructure.InventoryClient.Meta;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP flows of offers and their compositions against PostgreSQL (Testcontainers), with Inventory
 * replaced by a mock. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.generate_statistics=true" })
@AutoConfigureMockMvc
class CatalogOfferApiIntegrationTest {

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	private static final InventoryItem POTATOES = new InventoryItem(UUID.randomUUID(), "Potatoes", "g");

	private static final InventoryItem JUICE = new InventoryItem(UUID.randomUUID(), "Orange juice", "ml");

	private static final UUID UNKNOWN_ITEM = UUID.randomUUID();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private CatalogOfferService offerService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JsonMapper jsonMapper;

	@MockitoBean
	private InventoryClient inventoryClient;

	private UUID imageId;

	private UUID entryId;

	@BeforeEach
	void setUp() throws IOException {
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		imageRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png())).getId();
		entryId = entryRepository.save(new CatalogEntry(1L, "Hawaiian burger", "Tasty", imageId, List.of())).getId();
		when(inventoryClient.find(POTATOES.id())).thenReturn(Optional.of(POTATOES));
		when(inventoryClient.find(JUICE.id())).thenReturn(Optional.of(JUICE));
		when(inventoryClient.find(UNKNOWN_ITEM)).thenReturn(Optional.empty());
	}

	@Test
	void createsSeveralInactiveOffersForAnEntry() throws Exception {
		String response = createOffer(entryId, offerBody("Individual", "180.0"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.entryId").value(entryId.toString()))
				.andExpect(jsonPath("$.data.presentationTag").value("Individual"))
				.andExpect(jsonPath("$.data.basePrice").value(180.0))
				.andExpect(jsonPath("$.data.status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.image.id").value(imageId.toString()))
				.andExpect(jsonPath("$.data.composition.slots", hasSize(1)))
				.andExpect(jsonPath("$.data.composition.slots[0].slotId").value(not(SENT_ID)))
				.andExpect(jsonPath("$.data.composition.slots[0].name").value("Side"))
				.andExpect(jsonPath("$.data.composition.slots[0].required").value(true))
				.andExpect(jsonPath("$.data.composition.slots[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].quantity").value(1))
				.andExpect(jsonPath("$.data.composition.slots[0].course").value("entrada"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].optionId").value(not(SENT_ID)))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].sourceType").value("INVENTORY_ITEM"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].recipeId").value(nullValue()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].inventoryItemId")
						.value(POTATOES.id().toString()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].displayName").value("Fries"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].quantity").value(200))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].unit").value("g"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].source.id").value(POTATOES.id().toString()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].source.name").value("Potatoes"))
				.andReturn().getResponse().getContentAsString();
		String individual = JsonPath.read(response, "$.data.id");
		String family = create(offerBody("Family", "320"));
		UUID otherEntry = entryRepository.save(new CatalogEntry(1L, "Lemonade", "Fresh", imageId, List.of())).getId();
		createOffer(otherEntry, offerBody(null, "40"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.presentationTag").doesNotExist());

		mockMvc.perform(get(OFFERS + "/" + individual))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.presentationTag").value("Individual"));
		mockMvc.perform(get(OFFERS).param("entryId", entryId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].presentationTag", contains("Family", "Individual")))
				.andExpect(jsonPath("$.data[0].id").value(family))
				.andExpect(jsonPath("$.data[0].entryName").value("Hawaiian burger"))
				.andExpect(jsonPath("$.data[0].slotCount").value(1))
				.andExpect(jsonPath("$.data[0].status").value("INACTIVE"))
				.andExpect(jsonPath("$.meta.total").value(2));
		mockMvc.perform(get(OFFERS).param("entryId", entryId.toString()).param("excludeOfferId", family))
				.andExpect(jsonPath("$.data[*].id", contains(individual)));
		mockMvc.perform(get(OFFERS).param("page", "2").param("pageSize", "2"))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.meta.total").value(3))
				.andExpect(jsonPath("$.meta.totalPages").value(2));
		mockMvc.perform(get(OFFERS).param("page", "0"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("page"));
		mockMvc.perform(get("/api/v1/menu/entries"))
				.andExpect(jsonPath("$.data[*].offerCount", contains(2, 1)));
	}

	@Test
	void createRejectsInvalidDataWithoutCreatingTheOffer() throws Exception {
		createOffer(UUID.randomUUID(), offerBody("Individual", "180")).andExpect(status().isNotFound());
		expectViolation(createOffer(entryId, offerBody("Individual", "180").replace(imageId.toString(),
				UUID.randomUUID().toString())), "/imageId");
		expectViolation(createOffer(entryId, offerBody("Individual", "180").replace("\"" + imageId + "\"", "null")),
				"/imageId");
		expectViolation(createOffer(entryId, """
				{"basePrice": 10, "imageId": "%s"}""".formatted(imageId)), "/composition/slots");
		expectViolation(createOffer(entryId, """
				{"basePrice": 10, "imageId": "%s", "composition": {"slots": []}}""".formatted(imageId)),
				"/composition/slots");
		expectViolation(createOffer(entryId, offerBody("Individual", "-1")), "/basePrice");
		expectViolation(createOffer(entryId, offerWith(option(UNKNOWN_ITEM, "g"))),
				"/composition/slots/0/options/0/inventoryItemId");
		expectViolation(createOffer(entryId, offerWith(option(POTATOES.id(), "kg"))),
				"/composition/slots/0/options/0/unit");
		expectViolation(createOffer(entryId, offerWith(option(POTATOES.id(), "g").replace("200", "0"))),
				"/composition/slots/0/options/0/quantity");
		expectViolation(createOffer(entryId, offerWith(option(POTATOES.id(), "g").replace("INVENTORY_ITEM", "RECIPE"))),
				"/composition/slots/0/options/0/sourceType");
		expectViolation(createOffer(entryId, offerBody("Individual", "180").replace("entrada", "snack")),
				"/composition/slots/0/course");
		expectViolation(createOffer(entryId, offerBody("Individual", "180").replace("\"quantity\": 1", "\"quantity\": 1.5")),
				"/composition/slots/0/quantity");
		expectViolation(createOffer(entryId, offerBody("Individual", "180").replace("\"required\": true",
				"\"required\": false")), "/composition/slots/0/required");

		assertEquals(0, offerRepository.count());
	}

	@Test
	void getAndListAnswerConditionalRequests() throws Exception {
		String id = create(offerBody("Individual", "180"));

		mockMvc.perform(get(OFFERS + "/" + id).header(HttpHeaders.IF_NONE_MATCH, "\"rev-0\""))
				.andExpect(status().isNotModified());
		String listEtag = mockMvc.perform(get(OFFERS)).andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		mockMvc.perform(get(OFFERS).header(HttpHeaders.IF_NONE_MATCH, listEtag))
				.andExpect(status().isNotModified());
		mockMvc.perform(get(OFFERS + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/not-a-uuid")).andExpect(status().isNotFound());
	}

	@Test
	void patchRequiresTheCurrentEtag() throws Exception {
		String id = create(offerBody("Individual", "180"));

		mockMvc.perform(patch(OFFERS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isPreconditionRequired());
		patchOffer(id, "\"rev-7\"", "{\"basePrice\": 1}").andExpect(status().isPreconditionFailed());
		patchOffer(id, "*", "{\"basePrice\": 1}").andExpect(status().isPreconditionFailed());
		patchOffer(id, "W/\"rev-0\"", "{\"basePrice\": 1}").andExpect(status().isPreconditionFailed());
		patchOffer(UUID.randomUUID().toString(), "\"rev-0\"", "{}").andExpect(status().isNotFound());

		mockMvc.perform(get(OFFERS + "/" + id)).andExpect(jsonPath("$.data.basePrice").value(180));
		patchOffer(id, "\"rev-0\"", "{\"basePrice\": 185, \"presentationTag\": \"Big\"}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, not("\"rev-0\"")))
				.andExpect(jsonPath("$.data.basePrice").value(185))
				.andExpect(jsonPath("$.data.presentationTag").value("Big"))
				.andExpect(jsonPath("$.data.composition.slots", hasSize(1)));
	}

	@Test
	void patchKeepsTheIdentityOfSlotsAndOptionsAndReplacesTheSentSets() throws Exception {
		String id = create(offerWith(option(POTATOES.id(), "g"), option(JUICE.id(), "ml")));
		String json = read(id);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String fries = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");
		clearInvocations(inventoryClient);

		// Only the quantity changes, so Inventory is not asked; the juice option is removed.
		String patched = patchOffer(id, etag(id), """
				{"composition": {"slots": [
				  {"slotId": "%s", "course": null, "options": [
				    {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "quantity": 250}]},
				  {"name": "Drink", "quantity": 2, "course": "bebida", "options": [%s]}]}}
				""".formatted(slot, fries, option(JUICE.id(), "ml")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].slotId").value(slot))
				.andExpect(jsonPath("$.data.composition.slots[0].name").value("Side"))
				.andExpect(jsonPath("$.data.composition.slots[0].course").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options", hasSize(1)))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].optionId").value(fries))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].quantity").value(250))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].displayName").value("Fries"))
				.andExpect(jsonPath("$.data.composition.slots[1].name").value("Drink"))
				.andExpect(jsonPath("$.data.composition.slots[1].quantity").value(2))
				.andReturn().getResponse().getContentAsString();
		verify(inventoryClient, never()).find(POTATOES.id());
		String drink = JsonPath.read(patched, "$.data.composition.slots[1].slotId");

		// A change of an option only is reflected in the ETag; omitting options keeps them.
		String before = etag(id);
		patchOffer(id, before, """
				{"composition": {"slots": [
				  {"slotId": "%s", "options": [{"optionId": "%s", "status": "INACTIVE"}]},
				  {"slotId": "%s"}]}}
				""".formatted(slot, fries, drink))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, not(before)))
				.andExpect(jsonPath("$.data.composition.slots[0].status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[1].options", hasSize(1)));
		assertNotEquals(before, etag(id));

		// Changing the article checks the unit against the new article.
		patchOffer(id, etag(id), """
				{"composition": {"slots": [
				  {"slotId": "%s", "options": [{"optionId": "%s", "inventoryItemId": "%s", "unit": "ml"}]}]}}
				""".formatted(slot, fries, JUICE.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots", hasSize(1)))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].source.name").value("Orange juice"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].unit").value("ml"));
	}

	@Test
	void losingTheLastActiveSlotInactivatesTheOfferAndRecoveringItReactivatesIt() throws Exception {
		String id = create(offerBody("Individual", "180"));
		String json = read(id);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String option = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");

		patchOffer(id, etag(id), "{\"status\": \"ACTIVE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		patchOffer(id, etag(id), optionStatus(slot, option, "INACTIVE"))
				.andExpect(jsonPath("$.data.status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].course").value("entrada"))
				.andExpect(jsonPath("$.data.composition.slots[0].status").value("INACTIVE"));
		patchOffer(id, etag(id), optionStatus(slot, option, "ACTIVE"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// An explicit inactivation prevails until a new administrative activation.
		patchOffer(id, etag(id), "{\"status\": \"INACTIVE\"}").andExpect(jsonPath("$.data.status").value("INACTIVE"));
		patchOffer(id, etag(id), optionStatus(slot, option, "INACTIVE")).andExpect(status().isOk());
		patchOffer(id, etag(id), optionStatus(slot, option, "ACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		patchOffer(id, etag(id), "{\"status\": \"ACTIVE\"}").andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// Activation needs an ACTIVE slot, also when both change in the same request.
		String inactiveWithActivation = optionStatus(slot, option, "INACTIVE").replace("{\"composition\"",
				"{\"status\": \"INACTIVE\", \"composition\"");
		patchOffer(id, etag(id), inactiveWithActivation).andExpect(status().isOk());
		expectViolation(patchOffer(id, etag(id), "{\"status\": \"ACTIVE\"}"), "/status");
		expectViolation(patchOffer(id, etag(id), optionStatus(slot, option, "INACTIVE")
				.replace("{\"composition\"", "{\"status\": \"ACTIVE\", \"composition\"")), "/status");
	}

	@Test
	void anInvalidUpdateIsRejectedAsAWhole() throws Exception {
		String id = create(offerWith(option(POTATOES.id(), "g"), option(JUICE.id(), "ml")));
		String other = create(offerBody("Other", "10"));
		String json = read(id);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String fries = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");
		String foreignSlot = JsonPath.read(read(other), "$.data.composition.slots[0].slotId");
		String etag = etag(id);

		String prefix = "{\"presentationTag\": \"Changed\", \"composition\": {\"slots\": [";
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + UUID.randomUUID() + "\"}]}}",
				"/composition/slots/0/slotId");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + foreignSlot + "\"}]}}", "/composition/slots/0/slotId");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\"}, {\"slotId\": \"" + slot + "\"}]}}",
				"/composition/slots/1/slotId");
		expectRejected(id, etag, prefix + "]}}", "/composition/slots");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": []}]}}",
				"/composition/slots/0/options");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \"" + fries
				+ "\"}, {\"optionId\": \"" + fries + "\"}]}]}}", "/composition/slots/0/options/1/optionId");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \"" + fries
				+ "\", \"unit\": \"kg\"}]}]}}", "/composition/slots/0/options/0/unit");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \"" + fries
				+ "\", \"inventoryItemId\": \"" + UNKNOWN_ITEM + "\"}]}]}}", "/composition/slots/0/options/0/inventoryItemId");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \"" + fries
				+ "\", \"quantity\": -1}]}]}}", "/composition/slots/0/options/0/quantity");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\"}, {\"quantity\": 1, \"options\": ["
				+ option(POTATOES.id(), "g") + "]}]}}", "/composition/slots/1/name");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"imageId\": null}", "/imageId");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"entryId\": \"" + entryId + "\"}", "/entryId");
		expectRejected(id, etag, "{\"composition\": {\"extra\": 1}}", "/composition/extra");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"status\": \"ACTIVE\"}]}}",
				"/composition/slots/0/status");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \"" + fries
				+ "\", \"sourceType\": \"INVENTORY_ITEM\", \"recipeId\": \"" + UUID.randomUUID() + "\"}]}]}}",
				"/composition/slots/0/options/0/recipeId");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"imageId\": \"" + UUID.randomUUID() + "\"}",
				"/imageId");

		when(inventoryClient.find(JUICE.id())).thenThrow(new ResourceAccessException("down"));
		patchOffer(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": [{\"optionId\": \"" + fries
				+ "\", \"inventoryItemId\": \"" + JUICE.id() + "\", \"unit\": \"ml\"}]}]}}")
				.andExpect(status().isServiceUnavailable());

		mockMvc.perform(get(OFFERS + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, etag))
				.andExpect(jsonPath("$.data.presentationTag").value("Individual"))
				.andExpect(jsonPath("$.data.composition.slots[0].options", hasSize(2)));
	}

	@Test
	void aConcurrentCompositionChangeThatPassedTheEtagCheckFailsOnCommit() throws Exception {
		String id = create(offerBody("Individual", "180"));
		String json = read(id);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String option = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");
		UUID offerId = UUID.fromString(id);
		TransactionTemplate slowRequest = new TransactionTemplate(transactionManager);
		TransactionTemplate fastRequest = new TransactionTemplate(transactionManager);
		fastRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		// Only options change, so the offer row is not written until the version increment on commit.
		assertThrows(OptimisticLockingFailureException.class, () -> slowRequest.executeWithoutResult(_ -> {
			offerRepository.findById(offerId).orElseThrow();
			fastRequest.executeWithoutResult(_ -> offerService.update(offerId, 0, input(optionStatus(slot, option,
					"INACTIVE"))));
			offerService.update(offerId, 0, input(optionStatus(slot, option, "INACTIVE").replace("INACTIVE", "ACTIVE")));
		}));

		mockMvc.perform(get(OFFERS + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\""))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].status").value("INACTIVE"));
	}

	@Test
	void listingLoadsEntriesAndSlotsInBatches() throws Exception {
		for (int i = 0; i < 5; i++) {
			UUID entry = entryRepository.save(new CatalogEntry(1L, "Entry " + i, "Tasty", imageId, List.of())).getId();
			createOffer(entry, offerBody("Individual", "10")).andExpect(status().isCreated());
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		mockMvc.perform(get(OFFERS)).andExpect(jsonPath("$.data", hasSize(5)));

		// The page, its entries and its slots, whatever the page size; one query per offer would be 11.
		assertEquals(3, statistics.getPrepareStatementCount());
	}

	@Test
	void searchesInventoryItems() throws Exception {
		when(inventoryClient.search("pot", 1, 12))
				.thenReturn(new InventoryItemPage(List.of(POTATOES), new Meta(1, 12, 1, 1)));

		mockMvc.perform(get("/api/v1/inventory/items").param("q", "pot"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(POTATOES.id().toString()))
				.andExpect(jsonPath("$.data[0].name").value("Potatoes"))
				.andExpect(jsonPath("$.data[0].unit").value("g"))
				.andExpect(jsonPath("$.meta.total").value(1))
				.andExpect(jsonPath("$.meta.totalPages").value(1));
		mockMvc.perform(get("/api/v1/inventory/items").param("pageSize", "0"))
				.andExpect(status().isUnprocessableContent());

		when(inventoryClient.search(any(), anyInt(), eq(5))).thenThrow(new ResourceAccessException("down"));
		mockMvc.perform(get("/api/v1/inventory/items").param("pageSize", "5"))
				.andExpect(status().isServiceUnavailable());
	}

	/** The client sends an id that the server must not use. */
	private static final String SENT_ID = "b37c1bd7-17b2-47a2-a3a4-802559445301";

	private void expectRejected(String id, String etag, String json, String path) throws Exception {
		expectViolation(patchOffer(id, etag, json), path);
		assertEquals(etag, etag(id));
	}

	private static void expectViolation(ResultActions result, String path) throws Exception {
		result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath(VIOLATION_PATH).value(path));
	}

	private String create(String json) throws Exception {
		return JsonPath.read(createOffer(entryId, json).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");
	}

	private String read(String id) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + id)).andReturn().getResponse().getContentAsString();
	}

	private String etag(String id) throws Exception {
		MvcResult result = mockMvc.perform(get(OFFERS + "/" + id)).andReturn();
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private ResultActions createOffer(UUID entry, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/menu/entries/" + entry + "/offers")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions patchOffer(String id, String ifMatch, String json) throws Exception {
		return mockMvc.perform(patch(OFFERS + "/" + id).header(HttpHeaders.IF_MATCH, ifMatch)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private OfferInput input(String json) {
		return jsonMapper.readValue(json, OfferInput.class);
	}

	private static String optionStatus(String slot, String option, String status) {
		return """
				{"composition": {"slots": [{"slotId": "%s", "options": [{"optionId": "%s", "status": "%s"}]}]}}
				""".formatted(slot, option, status);
	}

	/** Shaped like the contract example: the client sends status ACTIVE and ids, both ignored. */
	private String offerBody(String tag, String price) {
		String tagField = tag == null ? "" : "\"presentationTag\": \"" + tag + "\", ";
		return """
				{%s"basePrice": %s, "status": "ACTIVE", "imageId": "%s", "composition": {"slots": [
				  {"slotId": "%s", "name": "Side", "required": true, "quantity": 1, "course": "entrada",
				   "options": [%s]}]}}
				""".formatted(tagField, price, imageId, SENT_ID, option(POTATOES.id(), "g").replace("{",
				"{\"optionId\": \"" + SENT_ID + "\", \"recipeId\": null, "));
	}

	private String offerWith(String... options) {
		return """
				{"presentationTag": "Individual", "basePrice": 180, "imageId": "%s", "composition": {"slots": [
				  {"name": "Side", "quantity": 1, "options": [%s]}]}}
				""".formatted(imageId, String.join(", ", options));
	}

	private static String option(UUID item, String unit) {
		String name = item.equals(JUICE.id()) ? "Juice" : "Fries";
		return """
				{"displayName": "%s", "status": "ACTIVE", "sourceType": "INVENTORY_ITEM", "inventoryItemId": "%s",
				 "quantity": 200, "unit": "%s"}""".formatted(name, item, unit);
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
