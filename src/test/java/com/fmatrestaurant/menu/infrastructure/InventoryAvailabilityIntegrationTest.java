package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fmatrestaurant.menu.AvailabilityEvents;
import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.RabbitMqTestcontainersConfiguration;
import com.fmatrestaurant.menu.TestSecurityConfiguration;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.Image;
import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.listener.InventoryAvailabilityListener;
import com.fmatrestaurant.menu.infrastructure.rabbit.RabbitMqConfig;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;
import com.fmatrestaurant.menu.infrastructure.repository.SlotOptionRepository;
import com.jayway.jsonpath.JsonPath;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * BE-10: Menu applies the availability that Inventory publishes in {@code inventory.source.availability.updated}
 * (AsyncAPI 1.0.0) and keeps it for each content source, a recipe or an {@code INVENTORY_ITEM} option. The
 * exposure of {@code isAvailable} in the responses is BE-11. Requires Docker.
 */
@Import({ TestcontainersConfiguration.class, RabbitMqTestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.rabbitmq.listener.simple.auto-startup=true",
		"spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
		"spring.rabbitmq.listener.simple.retry.max-interval=100ms" })
@AutoConfigureMockMvc
class InventoryAvailabilityIntegrationTest {

	private static final String RECIPES = "/api/v1/menu/recipes";

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final InventoryItem BEEF = new InventoryItem("INV-beef-001", "Beef patty", "g");

	private static final InventoryItem POTATOES = new InventoryItem("INV-potatoes-001", "Potatoes", "g");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private ProcessedEventRepository processedEvents;

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

	@Autowired
	private SlotOptionRepository optionRepository;

	@Value("${menu.rabbitmq.inventory.availability-routing-key}")
	private String routingKey;

	private AvailabilityEvents events;

	private UUID imageId;

	private UUID entryId;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger listenerLog = (Logger) LoggerFactory.getLogger(InventoryAvailabilityListener.class);

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
		events = new AvailabilityEvents(rabbitTemplate, processedEvents, routingKey);
		amqpAdmin.purgeQueue(RabbitMqConfig.AVAILABILITY_DLQ, true);
		logs.start();
		listenerLog.addAppender(logs);
	}

	@AfterEach
	void tearDown() {
		listenerLog.detachAppender(logs);
	}

	@Test
	@DisplayName("Una opción INVENTORY_ITEM no está disponible hasta que Inventario lo comunica (prepara TC-DISP-01-CA2a/CA2b)")
	void anInventoryOptionTakesTheAvailabilityInventoryCommunicates() throws Exception {
		UUID option = inventoryOption();
		assertFalse(sourceAvailable(option), "a new option is not available until Inventory says so");

		events.publish(option, true);
		assertTrue(sourceAvailable(option));

		events.publish(option, false);
		assertFalse(sourceAvailable(option));
	}

	@Test
	@DisplayName("La disponibilidad de una receta vale para todas sus revisiones")
	void aRecipeEventSetsEveryRevision() throws Exception {
		UUID recipe = recipe();
		newRevision(recipe, 0);
		assertFalse(revisionAvailable(recipe, 1));
		assertFalse(revisionAvailable(recipe, 2));

		events.publish(recipe, true);
		assertTrue(revisionAvailable(recipe, 1));
		assertTrue(revisionAvailable(recipe, 2));

		events.publish(recipe, false);
		assertFalse(revisionAvailable(recipe, 1));
		assertFalse(revisionAvailable(recipe, 2));
	}

	@Test
	@DisplayName("Una revisión nueva conserva la disponibilidad que Inventario comunicó para la receta")
	void aNewRevisionKeepsTheAvailabilityOfTheRecipe() throws Exception {
		UUID recipe = recipe();
		events.publish(recipe, true);

		newRevision(recipe, 0);

		assertTrue(revisionAvailable(recipe, 2));
	}

	@Test
	@DisplayName("Un evento repetido no tiene efecto")
	void aRepeatedEventHasNoEffect() throws Exception {
		UUID option = inventoryOption();
		String first = events.publish(option, false);
		events.publish(option, true);

		events.republish(first, option, false);

		assertTrue(sourceAvailable(option), "the repeated event must not undo the later one");
		assertTrue(processedEvents.existsById(first));
	}

	@Test
	@DisplayName("Un evento de un origen desconocido o de una opción con receta no cambia nada y no va a la DLQ")
	void anEventForAnotherSourceChangesNothing() throws Exception {
		UUID recipe = recipe();
		UUID recipeOption = recipeOption(recipe);

		events.publish(recipeOption, true);
		UUID unknown = UUID.randomUUID();
		events.publish(unknown, true);

		assertFalse(revisionAvailable(recipe, 1), "an option id never changes the availability of its recipe");
		assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(unknown.toString())
				&& event.getFormattedMessage().contains("unknown source")));
		assertNull(rabbitTemplate.receive(RabbitMqConfig.AVAILABILITY_DLQ, 500));
	}

	@Test
	@DisplayName("Un evento sin isAvailable incumple el contrato y termina en la DLQ")
	void anEventWithoutAvailabilityEndsInTheDeadLetterQueue() throws Exception {
		UUID option = inventoryOption();

		events.send(UUID.randomUUID().toString(), "{\"sourceId\": \"" + option + "\"}");

		Message dead = rabbitTemplate.receive(RabbitMqConfig.AVAILABILITY_DLQ, 15_000);
		assertNotNull(dead, "the message must reach the dead-letter queue");
		assertTrue(new String(dead.getBody(), StandardCharsets.UTF_8).contains(option.toString()));
		assertFalse(sourceAvailable(option));
	}

	@Test
	@DisplayName("Sin messageId se usa el eventId del cuerpo")
	void theEventIdOfTheBodyIsTheFallback() throws Exception {
		UUID option = inventoryOption();
		String eventId = UUID.randomUUID().toString();

		events.send(null, "{\"eventId\": \"" + eventId + "\", \"sourceId\": \"" + option
				+ "\", \"isAvailable\": true}");
		events.awaitDrained();

		assertTrue(processedEvents.existsById(eventId));
		assertTrue(sourceAvailable(option));
	}

	private boolean sourceAvailable(UUID option) {
		Boolean available = optionRepository.findSourceAvailability(option);
		assertNotNull(available, "unknown option " + option);
		return available;
	}

	private boolean revisionAvailable(UUID recipe, int revision) {
		return revisionRepository.findByRecipeIdAndRevision(recipe, revision).orElseThrow().isAvailable();
	}

	private UUID recipe() throws Exception {
		String json = mockMvc.perform(post(RECIPES).header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Burger\", \"description\": \"Classic\", \"instructions\": \"Grill and serve\", "
						+ "\"ingredients\": [{\"inventoryItemId\": \"" + BEEF.id() + "\", \"quantity\": 2}]}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(json, "$.data.id"));
	}

	private void newRevision(UUID recipe, int version) throws Exception {
		mockMvc.perform(patch(RECIPES + "/" + recipe).header(HttpHeaders.IF_MATCH, "\"rev-" + version + "\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Better burger\"}"))
				.andExpect(status().isOk());
	}

	private UUID inventoryOption() throws Exception {
		return createOffer("{\"displayName\": \"Fries\", \"sourceType\": \"INVENTORY_ITEM\", \"inventoryItemId\": \""
				+ POTATOES.id() + "\", \"quantity\": 200}");
	}

	private UUID recipeOption(UUID recipe) throws Exception {
		return createOffer("{\"displayName\": \"House burger\", \"sourceType\": \"RECIPE\", \"recipeId\": \""
				+ recipe + "\"}");
	}

	/** Creates an offer with a single option and returns the id of that option. */
	private UUID createOffer(String option) throws Exception {
		String json = mockMvc.perform(post("/api/v1/menu/entries/" + entryId + "/offers")
				.header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"presentationTag\": \"Individual\", \"basePrice\": 180, \"imageId\": \"" + imageId
						+ "\", \"composition\": {\"slots\": [{\"name\": \"Main\", \"quantity\": 1, \"options\": ["
						+ option + "]}]}}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String offer = JsonPath.read(json, "$.data.id");
		String detail = mockMvc.perform(get(OFFERS + "/" + offer)).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(detail, "$.data.composition.slots[0].options[0].optionId"));
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
