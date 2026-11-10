package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import com.fmatrestaurant.menu.RabbitMqTestcontainersConfiguration;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.Image;
import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The contract of Menu with the rest of the system: what is published in {@code menu.topic}, with which routing
 * key and payload, when a recipe or an Inventory option is created, updated or deleted. Requires Docker.
 */
@Import({ TestcontainersConfiguration.class, RabbitMqTestcontainersConfiguration.class })
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class SourceEventsBrokerIntegrationTest {

	private static final String QUEUE = "test.menu.source.events";

	private static final String RECIPES = "/api/v1/menu/recipes";

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final long TIMEOUT_MS = 5000;

	private static final InventoryItem BEEF = new InventoryItem("INV-beef-001", "Beef patty", "g");

	private static final InventoryItem POTATOES = new InventoryItem("INV-potatoes-001", "Potatoes", "g");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeRevisionRepository revisionRepository;

	private UUID imageId;

	private UUID entryId;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws IOException {
		InventoryStub.INSTANCE.reset();
		InventoryStub.INSTANCE.add(BEEF);
		InventoryStub.INSTANCE.add(POTATOES);
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		imageRepository.deleteAll();
		revisionRepository.deleteAll();
		recipeRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png())).getId();
		entryId = entryRepository.save(new CatalogEntry(1L, "Burgers", "Tasty", imageId, List.of())).getId();

		TopicExchange exchange = new TopicExchange("menu.topic", true, false);
		amqpAdmin.declareExchange(exchange);
		Queue queue = new Queue(QUEUE, true, false, false);
		amqpAdmin.declareQueue(queue);
		amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("menu.source.#"));
		amqpAdmin.purgeQueue(QUEUE, true);
	}

	@AfterEach
	void deleteQueue() {
		amqpAdmin.deleteQueue(QUEUE);
	}

	@Test
	void aRecipeIsAnnouncedWhenItIsCreatedUpdatedAndDeleted() throws Exception {
		String id = JsonPath.read(mockMvc.perform(post(RECIPES).header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content(recipe("Burger", BEEF.id(), "2")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");

		Message created = receive();
		assertEquals("menu.source.created", created.getMessageProperties().getReceivedRoutingKey());
		assertNotNull(created.getMessageProperties().getMessageId());
		assertEquals(id, created.getMessageProperties().getCorrelationId());
		JsonNode body = jsonMapper.readTree(created.getBody());
		assertEquals(id, body.get("sourceId").asString());
		assertEquals("Burger", body.get("name").asString());
		assertEquals(BEEF.id(), body.get("items").get(0).get("id").asString());
		assertEquals(2, body.get("items").get(0).get("quantity").asInt());

		mockMvc.perform(patch(RECIPES + "/" + id).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Better burger\"}"))
				.andExpect(status().isOk());
		Message updated = receive();
		assertEquals("menu.source.updated", updated.getMessageProperties().getReceivedRoutingKey());
		assertEquals("Better burger", jsonMapper.readTree(updated.getBody()).get("name").asString());

		mockMvc.perform(delete(RECIPES + "/" + id).header(HttpHeaders.IF_MATCH, "\"rev-1\""))
				.andExpect(status().isNoContent());
		Message deleted = receive();
		assertEquals("menu.source.deleted", deleted.getMessageProperties().getReceivedRoutingKey());
		assertEquals(id, jsonMapper.readTree(deleted.getBody()).get("sourceId").asString());
		assertEquals(1, jsonMapper.readTree(deleted.getBody()).size());
	}

	@Test
	void anInventoryOptionIsAnnouncedWhenItIsCreatedUpdatedAndItsOfferDeleted() throws Exception {
		String offerId = JsonPath.read(createOffer(offer(POTATOES.id(), 200)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");
		String json = read(offerId);
		String slot = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String option = JsonPath.read(json, "$.data.composition.slots[0].options[0].optionId");

		Message created = receive();
		assertEquals("menu.source.created", created.getMessageProperties().getReceivedRoutingKey());
		JsonNode body = jsonMapper.readTree(created.getBody());
		assertEquals(option, body.get("sourceId").asString());
		assertTrue(body.get("name").isNull());
		assertEquals(POTATOES.id(), body.get("items").get(0).get("id").asString());
		assertEquals(200, body.get("items").get(0).get("quantity").asInt());

		mockMvc.perform(patch(OFFERS + "/" + offerId).header("Idempotency-Key", UUID.randomUUID())
				.header(HttpHeaders.IF_MATCH, etag(offerId)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"composition": {"slots": [{"slotId": "%s", "options": [
						  {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "quantity": 350}]}]}}
						""".formatted(slot, option)))
				.andExpect(status().isOk());
		Message updated = receive();
		assertEquals("menu.source.updated", updated.getMessageProperties().getReceivedRoutingKey());
		assertEquals(350, jsonMapper.readTree(updated.getBody()).get("items").get(0).get("quantity").asInt());

		mockMvc.perform(delete(OFFERS + "/" + offerId).header(HttpHeaders.IF_MATCH, etag(offerId)))
				.andExpect(status().isNoContent());
		Message deleted = receive();
		assertEquals("menu.source.deleted", deleted.getMessageProperties().getReceivedRoutingKey());
		assertEquals(option, jsonMapper.readTree(deleted.getBody()).get("sourceId").asString());
	}

	@Test
	void deletingAnEntryAnnouncesTheInventoryOptionsOfItsOffers() throws Exception {
		String offerId = JsonPath.read(createOffer(offer(POTATOES.id(), 200)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");
		String option = JsonPath.read(read(offerId), "$.data.composition.slots[0].options[0].optionId");
		receive();
		mockMvc.perform(patch("/api/v1/menu/entries/" + entryId).header(HttpHeaders.IF_MATCH,
				mockMvc.perform(get("/api/v1/menu/entries/" + entryId)).andReturn().getResponse()
						.getHeader(HttpHeaders.ETAG))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ARCHIVED\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/menu/entries/" + entryId).header(HttpHeaders.IF_MATCH,
				mockMvc.perform(get("/api/v1/menu/entries/" + entryId)).andReturn().getResponse()
						.getHeader(HttpHeaders.ETAG)))
				.andExpect(status().isNoContent());

		Message deleted = receive();
		assertEquals("menu.source.deleted", deleted.getMessageProperties().getReceivedRoutingKey());
		assertEquals(option, jsonMapper.readTree(deleted.getBody()).get("sourceId").asString());
	}

	@Test
	void aRejectedRequestAnnouncesNothing() throws Exception {
		mockMvc.perform(post(RECIPES).header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content(recipe("Burger", "unknown-item", "2")))
				.andExpect(status().isUnprocessableContent());
		createOffer(offer("unknown-item", 200)).andExpect(status().isUnprocessableContent());

		assertNull(rabbitTemplate.receive(QUEUE, 1000));
	}

	private Message receive() {
		Message message = rabbitTemplate.receive(QUEUE, TIMEOUT_MS);
		assertNotNull(message, "No message reached menu.topic");
		return message;
	}

	private ResultActions createOffer(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/menu/entries/" + entryId + "/offers")
				.header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String read(String offerId) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + offerId)).andReturn().getResponse().getContentAsString();
	}

	private String etag(String offerId) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + offerId)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private String offer(String item, int quantity) {
		return """
				{"presentationTag": "Individual", "basePrice": 180, "imageId": "%s", "composition": {"slots": [
				  {"name": "Side", "quantity": 1, "options": [
				    {"displayName": "Fries", "sourceType": "INVENTORY_ITEM", "inventoryItemId": "%s",
				     "quantity": %d, "unit": "g"}]}]}}
				""".formatted(imageId, item, quantity);
	}

	private static String recipe(String name, String item, String quantity) {
		return "{\"name\": \"" + name + "\", \"description\": \"Classic\", \"instructions\": \"Grill and serve\", "
				+ "\"supplies\": [{\"inventoryItemId\": \"" + item + "\", \"quantity\": " + quantity + "}]}";
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
