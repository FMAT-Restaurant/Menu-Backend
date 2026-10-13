package com.fmatrestaurant.menu.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CatalogOfferService;
import com.fmatrestaurant.menu.application.CatalogOfferService.OfferInput;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP flows of offers and their compositions against PostgreSQL (Testcontainers). Inventory articles
 * are confirmed by the real client against {@link InventoryStub}. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.generate_statistics=true" })
@AutoConfigureMockMvc
class CatalogOfferApiIntegrationTest {

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	private static final String FIRST_PAGE = "GET /api/v1/inventory/items?page=1&pageSize=100";

	private static final InventoryItem POTATOES = new InventoryItem(UUID.randomUUID(), "Potatoes", "g");

	private static final InventoryItem JUICE = new InventoryItem(UUID.randomUUID(), "Orange juice", "ml");

	private static final InventoryStub INVENTORY = InventoryStub.INSTANCE;

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

	private UUID imageId;

	private UUID entryId;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws IOException {
		INVENTORY.reset();
		INVENTORY.add(POTATOES);
		INVENTORY.add(JUICE);
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		imageRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png())).getId();
		entryId = entryRepository.save(new CatalogEntry(1L, "Hawaiian burger", "Tasty", imageId, List.of())).getId();
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
				.andExpect(jsonPath("$.data.composition.slots[0].slotId").exists())
				.andExpect(jsonPath("$.data.composition.slots[0].name").value("Side"))
				.andExpect(jsonPath("$.data.composition.slots[0].required").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].quantity").value(1))
				.andExpect(jsonPath("$.data.composition.slots[0].course").value("entrada"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].optionId").exists())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].sourceType").value("INVENTORY_ITEM"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].inventoryItemId")
						.value(POTATOES.id().toString()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].displayName").value("Fries"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].quantity").value(200))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].unit").value("g"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].source").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].recipeId").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].recipeRevision").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		assertEquals(List.of(FIRST_PAGE), INVENTORY.requests());
		String individual = JsonPath.read(response, "$.data.id");
		String family = create(offerBody("Family", "320"));
		UUID otherEntry = entryRepository.save(new CatalogEntry(1L, "Lemonade", "Fresh", imageId, List.of())).getId();
		createOffer(otherEntry, offerBody(null, "40"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.presentationTag").doesNotExist());

		mockMvc.perform(get(OFFERS + "/" + individual))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.presentationTag").value("Individual"))
				.andExpect(jsonPath("$.data.composition.slots[0].required").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].source").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].recipeId").doesNotExist());
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
		expectViolation(createOffer(entryId, """
				{"basePrice": 10, "imageId": "%s", "composition": {"slots": null}}""".formatted(imageId)),
				"/composition/slots");
		expectViolation(createOffer(entryId, offerBody("Individual", "-1")), "/basePrice");
		expectViolation(createOffer(entryId, offerWith(option(POTATOES.id(), "g")
				.replace("\"inventoryItemId\": \"" + POTATOES.id() + "\", ", ""))),
				"/composition/slots/0/options/0/inventoryItemId");
		expectViolation(createOffer(entryId, offerWith(option(UUID.randomUUID(), "g"))),
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

		assertEquals(0, offerRepository.count());
	}

	@Test
	void createRejectsFieldsThatItsSchemaDoesNotReceive() throws Exception {
		String body = offerBody("Individual", "180");
		String slot = "{\"name\": \"Side\"";
		String option = "{\"displayName\": \"Fries\"";

		expectViolation(createOffer(entryId, body.replaceFirst("\\{", "{\"status\": \"ACTIVE\", ")), "/status");
		expectViolation(createOffer(entryId, body.replaceFirst("\\{", "{\"entryId\": \"" + entryId + "\", ")),
				"/entryId");
		expectViolation(createOffer(entryId, body.replace("{\"slots\"", "{\"extra\": 1, \"slots\"")),
				"/composition/extra");
		expectViolation(createOffer(entryId, body.replace(slot, "{\"slotId\": \"" + UUID.randomUUID() + "\", "
				+ slot.substring(1))), "/composition/slots/0/slotId");
		expectViolation(createOffer(entryId, body.replace(slot, "{\"required\": true, " + slot.substring(1))),
				"/composition/slots/0/required");
		expectViolation(createOffer(entryId, body.replace(option, "{\"optionId\": \"" + UUID.randomUUID() + "\", "
				+ option.substring(1))), "/composition/slots/0/options/0/optionId");
		expectViolation(createOffer(entryId, body.replace(option, "{\"status\": \"ACTIVE\", " + option.substring(1))),
				"/composition/slots/0/options/0/status");
		expectViolation(createOffer(entryId, body.replace(option, "{\"recipeId\": null, " + option.substring(1))),
				"/composition/slots/0/options/0/recipeId");
		expectViolation(createOffer(entryId, body.replace(option, "{\"source\": {\"id\": \"" + POTATOES.id() + "\"}, "
				+ option.substring(1))), "/composition/slots/0/options/0/source");
		// null is not the same as a missing value; only PATCH accepts course null.
		expectViolation(createOffer(entryId, body.replace("\"entrada\"", "null")), "/composition/slots/0/course");
		expectViolation(createOffer(entryId, body.replace("\"Individual\"", "null")), "/presentationTag");
		expectViolation(createOffer(entryId, body.replace("\"basePrice\": 180", "\"basePrice\": null")), "/basePrice");
		expectViolation(createOffer(entryId, body.replace("\"quantity\": 200", "\"quantity\": null")),
				"/composition/slots/0/options/0/quantity");

		assertEquals(0, offerRepository.count());
	}

	@Test
	void getAndListAnswerConditionalRequests() throws Exception {
		String id = create(offerBody("Individual", "180"));

		mockMvc.perform(get(OFFERS + "/" + id).header(HttpHeaders.IF_NONE_MATCH, "\"rev-0\""))
				.andExpect(status().isNotModified());
		String listEtag = mockMvc.perform(get(OFFERS)).andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assert listEtag != null;
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
		patchOffer(id, "\"rev-9223372036854775808\"", "{\"basePrice\": 1}").andExpect(status().isPreconditionFailed());
		patchOffer(id, "\"rev-99999999999999999999999\"", "{\"basePrice\": 1}")
				.andExpect(status().isPreconditionFailed());
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
		// Only the quantity changes; the juice option is removed.
		String patched = patchOffer(id, etag(id), """
				{"composition": {"slots": [
				  {"slotId": "%s", "course": null, "options": [
				    {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "quantity": 250}]},
				  {"name": "Drink", "quantity": 2, "course": "bebida", "options": [%s]}]}}
				""".formatted(slot, fries, newOption(JUICE.id(), "ml")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].slotId").value(slot))
				.andExpect(jsonPath("$.data.composition.slots[0].name").value("Side"))
				.andExpect(jsonPath("$.data.composition.slots[0].course").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[0].options", hasSize(1)))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].optionId").value(fries))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].quantity").value(250))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].displayName").value("Fries"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].source").doesNotExist())
				.andExpect(jsonPath("$.data.composition.slots[1].name").value("Drink"))
				.andExpect(jsonPath("$.data.composition.slots[1].quantity").value(2))
				.andExpect(jsonPath("$.data.composition.slots[1].status").value("ACTIVE"))
				.andReturn().getResponse().getContentAsString();
		String drink = JsonPath.read(patched, "$.data.composition.slots[1].slotId");

		// A change of an option only is reflected in the ETag; omitting options keeps them.
		String before = etag(id);
		String after = patchOffer(id, before, """
				{"composition": {"slots": [
				  {"slotId": "%s", "options": [{"optionId": "%s", "sourceType": "INVENTORY_ITEM", "status": "INACTIVE"}]},
				  {"slotId": "%s"}]}}
				""".formatted(slot, fries, drink))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, not(before)))
				.andExpect(jsonPath("$.data.composition.slots[0].status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.composition.slots[1].options", hasSize(1)))
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		assertEquals(after, etag(id));

		// The ETag returned by PATCH can be used right away. A new article is checked against Inventory.
		patchOffer(id, after, """
				{"composition": {"slots": [
				  {"slotId": "%s", "options": [
				    {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "inventoryItemId": "%s", "unit": "ml"}]}]}}
				""".formatted(slot, fries, JUICE.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots", hasSize(1)))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].optionId").value(fries))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].inventoryItemId").value(JUICE.id().toString()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].unit").value("ml"));

		// Empty objects keep every value.
		patchOffer(id, etag(id), "{\"composition\": {}}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].optionId").value(fries));
		patchOffer(id, etag(id), "{}")
				.andExpect(status().isOk())
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
		String inSlot = prefix + "{\"slotId\": \"" + slot + "\", \"options\": [";
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + UUID.randomUUID() + "\"}]}}",
				"/composition/slots/0/slotId");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + foreignSlot + "\"}]}}", "/composition/slots/0/slotId");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\"}, {\"slotId\": \"" + slot + "\"}]}}",
				"/composition/slots/1/slotId");
		expectRejected(id, etag, prefix + "]}}", "/composition/slots");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": []}]}}",
				"/composition/slots/0/options");
		expectRejected(id, etag, inSlot + existing(fries, "") + ", " + existing(fries, "") + "]}]}}",
				"/composition/slots/0/options/1/optionId");
		expectRejected(id, etag, inSlot + existing(fries, ", \"unit\": \"kg\"") + "]}]}}",
				"/composition/slots/0/options/0/unit");
		expectRejected(id, etag, inSlot + existing(fries, ", \"inventoryItemId\": \"" + UUID.randomUUID() + "\"")
				+ "]}]}}", "/composition/slots/0/options/0/inventoryItemId");
		expectRejected(id, etag, inSlot + existing(fries, ", \"source\": {\"id\": \"" + POTATOES.id() + "\"}")
				+ "]}]}}", "/composition/slots/0/options/0/source");
		expectRejected(id, etag, inSlot + existing(fries, ", \"quantity\": -1") + "]}]}}",
				"/composition/slots/0/options/0/quantity");
		expectRejected(id, etag, inSlot + existing(fries, ", \"displayName\": null") + "]}]}}",
				"/composition/slots/0/options/0/displayName");
		expectRejected(id, etag, inSlot + "{\"optionId\": \"" + fries + "\", \"quantity\": 5}]}]}}",
				"/composition/slots/0/options/0/sourceType");
		expectRejected(id, etag, inSlot + existing(fries, "").replace("INVENTORY_ITEM", "RECIPE") + "]}]}}",
				"/composition/slots/0/options/0/sourceType");
		expectRejected(id, etag, inSlot + option(POTATOES.id(), "g") + "]}]}}",
				"/composition/slots/0/options/0/status");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\"}, {\"quantity\": 1, \"options\": ["
				+ newOption(POTATOES.id(), "g") + "]}]}}", "/composition/slots/1/name");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"name\": null}]}}",
				"/composition/slots/0/name");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"options\": null}]}}",
				"/composition/slots/0/options");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"composition\": null}", "/composition");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"basePrice\": null}", "/basePrice");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"imageId\": null}", "/imageId");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"entryId\": \"" + entryId + "\"}", "/entryId");
		expectRejected(id, etag, "{\"composition\": {\"extra\": 1}}", "/composition/extra");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"status\": \"ACTIVE\"}]}}",
				"/composition/slots/0/status");
		expectRejected(id, etag, prefix + "{\"slotId\": \"" + slot + "\", \"required\": true}]}}",
				"/composition/slots/0/required");
		expectRejected(id, etag, inSlot + existing(fries, ", \"recipeId\": \"" + UUID.randomUUID() + "\"") + "]}]}}",
				"/composition/slots/0/options/0/recipeId");
		expectRejected(id, etag, "{\"presentationTag\": \"Changed\", \"imageId\": \"" + UUID.randomUUID() + "\"}",
				"/imageId");
		mockMvc.perform(get(OFFERS + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, etag))
				.andExpect(jsonPath("$.data.presentationTag").value("Individual"))
				.andExpect(jsonPath("$.data.composition.slots[0].options", hasSize(2)));
	}

	@Test
	void inventoryIsConsultedOnlyForNewArticlesOrUnitsAndOncePerArticle() throws Exception {
		for (int i = 0; i < 100; i++) {
			INVENTORY.add("Filler " + i, "g");
		}
		InventoryItem cheddar = INVENTORY.add("Cheddar", "kg");
		INVENTORY.clearRequests();

		// The article is on the second page; repeated in the composition, it is consulted once.
		String id = create(offerWith(option(cheddar.id(), "kg"), option(cheddar.id(), "kg")));
		assertEquals(List.of(FIRST_PAGE, "GET /api/v1/inventory/items?page=2&pageSize=100"), INVENTORY.requests());

		String json = read(id);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String first = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");
		String second = JsonPath.read(json, "$.data.composition.slots[0].options[1].optionId");
		INVENTORY.clearRequests();
		patchOffer(id, etag(id), """
				{"composition": {"slots": [{"slotId": "%s", "options": [
				  {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "quantity": 3, "displayName": "Cheese",
				   "status": "INACTIVE"},
				  {"optionId": "%s", "sourceType": "INVENTORY_ITEM"}]}]}}
				""".formatted(slot, first, second))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].inventoryItemId").value(cheddar.id().toString()))
				.andExpect(jsonPath("$.data.composition.slots[0].options[0].displayName").value("Cheese"));
		assertEquals(List.of(), INVENTORY.requests());

		// Sending the unit checks it against the current unit of the stored article.
		patchOffer(id, etag(id), """
				{"composition": {"slots": [{"slotId": "%s", "options": [
				  {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "unit": "kg"}]}]}}
				""".formatted(slot, first))
				.andExpect(status().isOk());
		assertEquals(2, INVENTORY.requests().size());
	}

	@Test
	void anInventoryFailureRejectsTheWriteWithoutChanges() throws Exception {
		String id = create(offerBody("Individual", "180"));
		String json = read(id);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String option = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");
		String etag = etag(id);
		String unitChange = """
				{"basePrice": 1, "composition": {"slots": [{"slotId": "%s", "options": [
				  {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "unit": "g"}]}]}}
				""".formatted(slot, option);

		for (int failure : List.of(401, 403, 404, 429, 500, 503)) {
			INVENTORY.fail(failure, "{\"error\": {\"message\": \"No\"}}");
			expectBadGateway(createOffer(entryId, offerBody("Other", "10")));
			expectBadGateway(patchOffer(id, etag, unitChange));
		}
		for (String invalid : List.of("not json", "{\"data\": []}", "{\"meta\": {\"totalPages\": 1}}",
				"{\"data\": [{\"id\": \"" + POTATOES.id() + "\"}], \"meta\": {\"totalPages\": 1}}")) {
			INVENTORY.fail(200, invalid);
			expectBadGateway(createOffer(entryId, offerBody("Other", "10")));
			expectBadGateway(patchOffer(id, etag, unitChange));
		}
		INVENTORY.reset();
		INVENTORY.add(POTATOES);
		INVENTORY.answerSlowly();
		expectBadGateway(createOffer(entryId, offerBody("Other", "10")));
		expectBadGateway(patchOffer(id, etag, unitChange));

		assertEquals(1, offerRepository.count());
		mockMvc.perform(get(OFFERS + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, etag))
				.andExpect(jsonPath("$.data.basePrice").value(180));
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
			offerService.update(offerId, 0, input(optionStatus(slot, option, "ACTIVE")));
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

	private void expectRejected(String id, String etag, String json, String path) throws Exception {
		expectViolation(patchOffer(id, etag, json), path);
		assertEquals(etag, etag(id));
	}

	private static void expectViolation(ResultActions result, String path) throws Exception {
		result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath(VIOLATION_PATH).value(path));
	}

	private static void expectBadGateway(ResultActions result) throws Exception {
		result.andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.message").exists());
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
				{"composition": {"slots": [{"slotId": "%s", "options": [%s]}]}}
				""".formatted(slot, existing(option, ", \"status\": \"" + status + "\""));
	}

	/** Shaped like the contract example of POST: the server assigns every id and status. */
	private String offerBody(String tag, String price) {
		String tagField = tag == null ? "" : "\"presentationTag\": \"" + tag + "\", ";
		return """
				{%s"basePrice": %s, "imageId": "%s", "composition": {"slots": [
				  {"name": "Side", "quantity": 1, "course": "entrada", "options": [%s]}]}}
				""".formatted(tagField, price, imageId, option(POTATOES.id(), "g"));
	}

	private String offerWith(String... options) {
		return """
				{"presentationTag": "Individual", "basePrice": 180, "imageId": "%s", "composition": {"slots": [
				  {"name": "Side", "quantity": 1, "options": [%s]}]}}
				""".formatted(imageId, String.join(", ", options));
	}

	/** A new option of POST ({@code InventoryItemSlotOptionInput}). */
	private static String option(UUID item, String unit) {
		String name = item.equals(JUICE.id()) ? "Juice" : "Fries";
		return """
				{"displayName": "%s", "sourceType": "INVENTORY_ITEM", "inventoryItemId": "%s", "quantity": 200, \
				"unit": "%s"}""".formatted(name, item, unit);
	}

	/** A new option of PATCH, which also requires its status. */
	private static String newOption(UUID item, String unit) {
		return option(item, unit).replace("\"sourceType\"", "\"status\": \"ACTIVE\", \"sourceType\"");
	}

	/** An existing option of PATCH, with the given extra fields. */
	private static String existing(String option, String fields) {
		return "{\"optionId\": \"" + option + "\", \"sourceType\": \"INVENTORY_ITEM\"" + fields + "}";
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
