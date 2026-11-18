package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fmatrestaurant.menu.AvailabilityEvents;
import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.RabbitMqTestcontainersConfiguration;
import com.fmatrestaurant.menu.TestSecurityConfiguration;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.Category;
import com.fmatrestaurant.menu.domain.model.Image;
import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-11: the derived, read-only {@code isAvailable} of options, slots, offers and entries (RF-DISP-01 to RF-DISP-06).
 * The recipes and Inventory options take their availability from events of Inventory published with
 * {@link AvailabilityEvents}. Requires Docker.
 */
@Import({ TestcontainersConfiguration.class, RabbitMqTestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.rabbitmq.listener.simple.auto-startup=true" })
@AutoConfigureMockMvc
class DerivedAvailabilityApiIntegrationTest {

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final String ENTRIES = "/api/v1/menu/entries";

	/** INV-CARNE. */
	private static final InventoryItem BEEF = new InventoryItem("CARNE-150G", "Carne 150 g", "g");

	private static final String OPTION_0 = "$.data.composition.slots[0].options[0]";

	private static final String OPTION_1 = "$.data.composition.slots[0].options[1]";

	private static final String SLOT_0 = "$.data.composition.slots[0]";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ProcessedEventRepository processedEvents;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Value("${menu.rabbitmq.inventory.availability-routing-key}")
	private String routingKey;

	private AvailabilityEvents events;

	private UUID imageId;

	private UUID entryId;

	/** REC-DISP: a recipe Inventory communicated as available. */
	private UUID recDisp;

	/** REC-NODISP: a recipe Inventory communicated as not available. */
	private UUID recNoDisp;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws Exception {
		InventoryStub.INSTANCE.reset();
		InventoryStub.INSTANCE.add(BEEF);
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
		imageRepository.deleteAll();
		events = new AvailabilityEvents(rabbitTemplate, processedEvents, routingKey);
		imageId = imageRepository.save(new Image("image/png", png())).getId();
		entryId = entryRepository.save(new CatalogEntry(1L, "Hamburguesas", "Del día", imageId, List.of())).getId();
		recDisp = recipe("Hamburguesa");
		events.publish(recDisp, true);
		recNoDisp = recipe("Pastel");
		events.publish(recNoDisp, false);
	}

	// ---------- RF-DISP-01: opción ----------

	@Test
	@DisplayName("TC-DISP-01-CA1a — Opción con receta comunicada disponible")
	void recipeOptionAvailable() throws Exception {
		String offer = offer(slot("Plato principal", recipeOption("Hamburguesa", recDisp, "ACTIVE")));

		getOffer(offer).andExpect(status().isOk()).andExpect(jsonPath(OPTION_0 + ".recipeId").value(recDisp.toString()))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-DISP-01-CA1b — Opción con receta comunicada no disponible")
	void recipeOptionNotAvailable() throws Exception {
		String offer = offer(slot("Plato principal", recipeOption("Pastel", recNoDisp, "ACTIVE")));

		getOffer(offer).andExpect(status().isOk()).andExpect(jsonPath(OPTION_0 + ".isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-DISP-01-CA2a/CA2b — Opciones de Inventario comunicadas disponible y no disponible (OFE-INV-FIX)")
	void inventoryOptions() throws Exception {
		String offer = offer(slot("Carne", inventoryOption("OPC-INV-DISP"), inventoryOption("OPC-INV-NODISP")));
		DocumentContext json = read(offer);
		events.publish(UUID.fromString(json.read(OPTION_0 + ".optionId")), true);
		events.publish(UUID.fromString(json.read(OPTION_1 + ".optionId")), false);

		getOffer(offer).andExpect(status().isOk())
				.andExpect(jsonPath(OPTION_0 + ".sourceType").value("INVENTORY_ITEM"))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(true))
				.andExpect(jsonPath(OPTION_1 + ".sourceType").value("INVENTORY_ITEM"))
				.andExpect(jsonPath(OPTION_1 + ".isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-DISP-01-CA3 — El estado administrativo no interviene en la disponibilidad de la opción")
	void optionStatusDoesNotMatter() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE"),
				recipeOption("NoDisp", recNoDisp, "ACTIVE")));

		patchOptions(offer, "INACTIVE", "INACTIVE").andExpect(status().isOk())
				.andExpect(jsonPath(OPTION_0 + ".status").value("INACTIVE"))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(true))
				.andExpect(jsonPath(OPTION_1 + ".status").value("INACTIVE"))
				.andExpect(jsonPath(OPTION_1 + ".isAvailable").value(false));
	}

	// ---------- RF-DISP-02 y RF-DISP-03: grupo y oferta ----------

	@Test
	@DisplayName("TC-DISP-02-CA1a — Grupo con al menos una opción activa y disponible")
	void slotWithAnActiveAvailableOption() throws Exception {
		String offer = offer(slot("Plato", recipeOption("NoDisp", recNoDisp, "ACTIVE"),
				recipeOption("Disp", recDisp, "ACTIVE")));

		getOffer(offer).andExpect(jsonPath(SLOT_0 + ".isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-DISP-02-CA1b — Grupo sin opciones activas y disponibles")
	void slotWithoutActiveAvailableOptions() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "INACTIVE"),
				recipeOption("NoDisp", recNoDisp, "ACTIVE")));

		getOffer(offer).andExpect(jsonPath(OPTION_0 + ".status").value("INACTIVE"))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(true))
				.andExpect(jsonPath(OPTION_1 + ".status").value("ACTIVE"))
				.andExpect(jsonPath(OPTION_1 + ".isAvailable").value(false))
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-DISP-03-CA1a — Oferta con todos sus grupos disponibles")
	void offerWithEveryGroupAvailable() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")),
				slot("Postre", recipeOption("Disp", recDisp, "ACTIVE")));

		getOffer(offer).andExpect(jsonPath("$.data.composition.slots[0].isAvailable").value(true))
				.andExpect(jsonPath("$.data.composition.slots[1].isAvailable").value(true))
				.andExpect(jsonPath("$.data.isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-DISP-03-CA1b — Oferta con un grupo no disponible")
	void offerWithAGroupNotAvailable() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")),
				slot("Postre", recipeOption("Pastel", recNoDisp, "ACTIVE")));

		getOffer(offer).andExpect(jsonPath("$.data.composition.slots[0].isAvailable").value(true))
				.andExpect(jsonPath("$.data.composition.slots[1].isAvailable").value(false))
				.andExpect(jsonPath("$.data.isAvailable").value(false));
		mockMvc.perform(get(OFFERS).param("entryId", entryId.toString()))
				.andExpect(jsonPath("$.data[0].isAvailable").value(false));
	}

	// ---------- RF-DISP-04: entrada ----------

	@Test
	@DisplayName("TC-DISP-04-CA1a — Entrada con una oferta activa y disponible")
	void entryWithAnActiveAvailableOffer() throws Exception {
		activateOffer(offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE"))));

		getEntry().andExpect(status().isOk()).andExpect(jsonPath("$.data.isAvailable").value(true));
		mockMvc.perform(get(ENTRIES)).andExpect(jsonPath("$.data[0].isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-DISP-04-CA1b — Entrada sin ofertas activas y disponibles")
	void entryWithoutActiveAvailableOffers() throws Exception {
		String available = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		String notAvailable = offer(slot("Plato", recipeOption("NoDisp", recNoDisp, "ACTIVE")));
		activateOffer(available);
		activateOffer(notAvailable);

		patchOffer(available, "{\"status\": \"INACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(true));
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(false));

		patchOffer(notAvailable, "{\"status\": \"INACTIVE\"}").andExpect(status().isOk());
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(false));
		mockMvc.perform(get(ENTRIES)).andExpect(jsonPath("$.data[0].isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-ENT-03-CA1 — Activar no exige ofertas, ofertas activas ni disponibles")
	void activatingAnEntryNeedsNoOffers() throws Exception {
		mockMvc.perform(patch(ENTRIES + "/" + entryId).header(HttpHeaders.IF_MATCH, etag(ENTRIES + "/" + entryId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	// ---------- RF-DISP-05 y RF-DISP-06: recálculo sin cambiar estados ----------

	@Test
	@DisplayName("TC-DISP-05-CA1b — Dejar de ser ordenable no cambia estados administrativos")
	void notOrderableKeepsTheStatuses() throws Exception {
		activateEntry();
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(offer);

		changeRecipe(offer, recNoDisp).andExpect(status().isOk())
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(false))
				.andExpect(jsonPath(OPTION_0 + ".status").value("ACTIVE"))
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(false))
				.andExpect(jsonPath("$.data.isAvailable").value(false))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		getEntry().andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-DISP-06-CA1 — El administrador no asigna la disponibilidad")
	void theAdministratorDoesNotAssignAvailability() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));

		MvcResult result = patchOffer(offer, "{\"isAvailable\": false}").andReturn();

		if (result.getResponse().getStatus() == 200) {
			assertTrue(JsonPath.<Boolean>read(result.getResponse().getContentAsString(), "$.data.isAvailable"));
		}
		getOffer(offer).andExpect(jsonPath("$.data.isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-DISP-06-CA2 — Propagación a grupo, oferta y entrada")
	void propagation() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(offer);
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(true));

		changeRecipe(offer, recNoDisp).andExpect(status().isOk())
				.andExpect(jsonPath(OPTION_0 + ".recipeId").value(recNoDisp.toString()))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(false))
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(false))
				.andExpect(jsonPath("$.data.isAvailable").value(false));
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-DISP-06-CA5 — El recálculo no cambia estados administrativos")
	void recalculationKeepsTheStatuses() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(offer);

		events.publish(recDisp, false);

		getOffer(offer).andExpect(jsonPath("$.data.isAvailable").value(false))
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(false))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath(OPTION_0 + ".status").value("ACTIVE"));
	}

	// ---------- Composición ----------

	@Test
	@DisplayName("TC-COMP-01-CA3 — La validez no depende de la disponibilidad")
	void validityDoesNotDependOnAvailability() throws Exception {
		MvcResult created = createOffer(slot("Plato", recipeOption("Pastel", recNoDisp, "ACTIVE")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.isAvailable").value(false))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(false)).andReturn();
		String offer = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		patchOffer(offer, "{\"status\": \"ACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-COMP-12-CA9 — La copia recalcula la disponibilidad")
	void copyRecalculates() throws Exception {
		String target = offer(slot("Plato principal", recipeOption("Hamburguesa", recDisp, "ACTIVE")));
		activateOffer(target);

		patchOffer(target, "{\"composition\": {\"slots\": [" + slot("Postre", recipeOption("Pastel", recNoDisp, "ACTIVE"))
				+ "]}}").andExpect(status().isOk())
				.andExpect(jsonPath(SLOT_0 + ".name").value("Postre"))
				.andExpect(jsonPath(OPTION_0 + ".displayName").value("Pastel"))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(false))
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(false))
				.andExpect(jsonPath("$.data.isAvailable").value(false))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	// ---------- Estados de ofertas y opciones ----------

	@Test
	@DisplayName("TC-GEN-04-CA6 — Estado, disponibilidad o eliminación de la oferta no cambian el estado de la entrada")
	void offerChangesKeepTheEntryStatus() throws Exception {
		// An ACTIVE entry keeps another ACTIVE offer, as the deletion requires.
		activateOffer(offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE"))));
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(offer);
		activateEntry();

		changeRecipe(offer, recNoDisp).andExpect(status().isOk()).andExpect(jsonPath("$.data.isAvailable").value(false));
		getEntry().andExpect(jsonPath("$.data.status").value("ACTIVE"));
		patchOffer(offer, "{\"status\": \"INACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		getEntry().andExpect(jsonPath("$.data.status").value("ACTIVE"));
		deleteOffer(offer).andExpect(status().isNoContent());
		getEntry().andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-OFE-04-CA2 — Activar no exige disponibilidad")
	void activatingAnOfferNeedsNoAvailability() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Pastel", recNoDisp, "ACTIVE")));

		patchOffer(offer, "{\"status\": \"ACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-OFE-04-CA5 — La activación recalcula la disponibilidad de la entrada")
	void activationRecalculatesTheEntry() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(false));

		patchOffer(offer, "{\"status\": \"ACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(true));
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-OFE-05-CA3 — La inactivación recalcula la disponibilidad de la entrada")
	void inactivationRecalculatesTheEntry() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(offer);
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(true));

		patchOffer(offer, "{\"status\": \"INACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-OFE-06-CA2 — La disponibilidad no interviene en la eliminación")
	void availabilityDoesNotMatterToDelete() throws Exception {
		String available = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		String notAvailable = offer(slot("Plato", recipeOption("Pastel", recNoDisp, "ACTIVE")));

		deleteOffer(available).andExpect(status().isNoContent());
		deleteOffer(notAvailable).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("TC-OFE-06-CA5 — La eliminación recalcula la disponibilidad de la entrada")
	void deletionRecalculatesTheEntry() throws Exception {
		String kept = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(kept);
		String deleted = offer(slot("Plato", recipeOption("Pastel", recNoDisp, "ACTIVE")));
		String before = etag(ENTRIES + "/" + entryId);

		deleteOffer(deleted).andExpect(status().isNoContent());

		getEntry().andExpect(jsonPath("$.data.isAvailable").value(true));
		assertEquals(before, etag(ENTRIES + "/" + entryId), "the entry did not change");
		patchOffer(kept, "{\"status\": \"INACTIVE\"}").andExpect(status().isOk());
		deleteOffer(kept).andExpect(status().isNoContent());
		getEntry().andExpect(jsonPath("$.data.isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-OPC-04-CA1 — Una opción activada vuelve a presentarse como disponible")
	void activatedOption() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "INACTIVE")));

		patchOptions(offer, "ACTIVE").andExpect(status().isOk())
				.andExpect(jsonPath(OPTION_0 + ".status").value("ACTIVE"))
				.andExpect(jsonPath(OPTION_0 + ".isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-OPC-04-CA2 — La activación recalcula la disponibilidad sin cambiar estados administrativos")
	void optionActivationRecalculates() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "INACTIVE"),
				recipeOption("NoDisp", recNoDisp, "ACTIVE")));
		activateOffer(offer);
		getOffer(offer).andExpect(jsonPath("$.data.isAvailable").value(false));

		patchOptions(offer, "ACTIVE", "ACTIVE").andExpect(status().isOk())
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(true))
				.andExpect(jsonPath("$.data.isAvailable").value(true))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	@DisplayName("TC-OPC-05-CA2 — La inactivación recalcula la disponibilidad sin cambiar estados administrativos")
	void optionInactivationRecalculates() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE"),
				recipeOption("NoDisp", recNoDisp, "ACTIVE")));
		activateOffer(offer);

		patchOptions(offer, "INACTIVE", "ACTIVE").andExpect(status().isOk())
				.andExpect(jsonPath(SLOT_0 + ".isAvailable").value(false))
				.andExpect(jsonPath("$.data.isAvailable").value(false))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	@DisplayName("TC-CAT-03-CA2 — La categoría eliminada deja de clasificar a sus entradas sin otro cambio")
	void deletingACategoryChangesNothingElse() throws Exception {
		Category kept = categoryRepository.save(new Category(1L, "Bebidas", "Frías"));
		Category deleted = categoryRepository.save(new Category(1L, "Comidas", "Calientes"));
		UUID entry = entryRepository.save(new CatalogEntry(1L, "Combo", "Completo", imageId, List.of(kept, deleted)))
				.getId();
		DocumentContext before = JsonPath.parse(mockMvc.perform(get(ENTRIES + "/" + entry))
				.andReturn().getResponse().getContentAsString());

		mockMvc.perform(delete("/api/v1/menu/categories/" + deleted.getId())
				.header(HttpHeaders.IF_MATCH, etag("/api/v1/menu/categories/" + deleted.getId())))
				.andExpect(status().isNoContent())
				.andExpect(result -> assertEquals(0, result.getResponse().getContentLength()));

		DocumentContext after = JsonPath.parse(mockMvc.perform(get(ENTRIES + "/" + entry))
				.andReturn().getResponse().getContentAsString());
		assertEquals(List.of(kept.getId().toString()), after.read("$.data.categoryIds"));
		for (String field : List.of("brandName", "description", "status", "isAvailable", "image")) {
			Object expected = before.read("$.data." + field);
			assertEquals(expected, after.read("$.data." + field), field);
		}
	}

	// ---------- ETag ----------

	@Test
	@DisplayName("Un cambio de disponibilidad cambia el ETag sin invalidar el If-Match anterior")
	void availabilityChangesTheEtagButNotTheIfMatch() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE")));
		activateOffer(offer);
		String offerEtag = etag(OFFERS + "/" + offer);
		String entryEtag = etag(ENTRIES + "/" + entryId);
		mockMvc.perform(get(OFFERS + "/" + offer).header(HttpHeaders.IF_NONE_MATCH, offerEtag))
				.andExpect(status().isNotModified());

		events.publish(recDisp, false);

		assertNotEquals(offerEtag, etag(OFFERS + "/" + offer));
		assertNotEquals(entryEtag, etag(ENTRIES + "/" + entryId));
		mockMvc.perform(get(OFFERS + "/" + offer).header(HttpHeaders.IF_NONE_MATCH, offerEtag))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.isAvailable").value(false));
		mockMvc.perform(get(ENTRIES + "/" + entryId).header(HttpHeaders.IF_NONE_MATCH, entryEtag))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.isAvailable").value(false));
		mockMvc.perform(patch(OFFERS + "/" + offer).header("Idempotency-Key", UUID.randomUUID())
				.header(HttpHeaders.IF_MATCH, offerEtag).contentType(MediaType.APPLICATION_JSON)
				.content("{\"basePrice\": 190}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch(ENTRIES + "/" + entryId).header(HttpHeaders.IF_MATCH, entryEtag)
				.contentType(MediaType.APPLICATION_JSON).content("{\"description\": \"Otra\"}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Las respuestas con isAvailable cumplen el contrato 8.0.1")
	void responsesConformToTheContract() throws Exception {
		String offer = offer(slot("Plato", recipeOption("Disp", recDisp, "ACTIVE"), inventoryOption("Carne")));

		getOffer(offer).andExpect(ContractValidation.conformsToContract());
		getEntry().andExpect(ContractValidation.conformsToContract());
		mockMvc.perform(get(OFFERS)).andExpect(ContractValidation.conformsToContract());
		mockMvc.perform(get(ENTRIES)).andExpect(ContractValidation.conformsToContract());
		// One ACTIVE option of the slot is available, the Inventory one is not yet communicated.
		assertTrue(read(offer).<Boolean>read("$.data.isAvailable"));
		assertFalse(read(offer).<Boolean>read(OPTION_1 + ".isAvailable"));
	}

	// ---------- Ayudantes ----------

	private UUID recipe(String name) throws Exception {
		String json = mockMvc.perform(post("/api/v1/menu/recipes").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"" + name + "\", \"description\": \"Receta\", \"instructions\": \"Preparar\", "
						+ "\"supplies\": [{\"inventoryItemId\": \"" + BEEF.id() + "\", \"quantity\": 150}]}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(json, "$.data.id"));
	}

	private static String recipeOption(String name, UUID recipe, String status) {
		return "{\"displayName\": \"" + name + "\", \"sourceType\": \"RECIPE\", \"recipeId\": \"" + recipe
				+ "\", \"status\": \"" + status + "\"}";
	}

	private static String inventoryOption(String name) {
		return "{\"displayName\": \"" + name + "\", \"sourceType\": \"INVENTORY_ITEM\", \"inventoryItemId\": \""
				+ BEEF.id() + "\", \"quantity\": 150}";
	}

	private static String slot(String name, String... options) {
		return "{\"name\": \"" + name + "\", \"quantity\": 1, \"options\": [" + String.join(", ", options) + "]}";
	}

	private ResultActions createOffer(String... slots) throws Exception {
		return mockMvc.perform(post(ENTRIES + "/" + entryId + "/offers").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"presentationTag\": \"Individual " + UUID.randomUUID() + "\", \"basePrice\": 180, "
						+ "\"imageId\": \"" + imageId + "\", \"composition\": {\"slots\": [" + String.join(", ", slots)
						+ "]}}"));
	}

	private String offer(String... slots) throws Exception {
		return JsonPath.read(createOffer(slots).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.data.id");
	}

	private ResultActions getOffer(String offer) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + offer));
	}

	private ResultActions getEntry() throws Exception {
		return mockMvc.perform(get(ENTRIES + "/" + entryId));
	}

	private DocumentContext read(String offer) throws Exception {
		return JsonPath.parse(getOffer(offer).andReturn().getResponse().getContentAsString());
	}

	private String etag(String path) throws Exception {
		return mockMvc.perform(get(path)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private ResultActions patchOffer(String offer, String body) throws Exception {
		return mockMvc.perform(patch(OFFERS + "/" + offer).header("Idempotency-Key", UUID.randomUUID())
				.header(HttpHeaders.IF_MATCH, etag(OFFERS + "/" + offer)).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions deleteOffer(String offer) throws Exception {
		return mockMvc.perform(delete(OFFERS + "/" + offer).header(HttpHeaders.IF_MATCH, etag(OFFERS + "/" + offer)));
	}

	private void activateOffer(String offer) throws Exception {
		patchOffer(offer, "{\"status\": \"ACTIVE\"}").andExpect(status().isOk());
	}

	private void activateEntry() throws Exception {
		mockMvc.perform(patch(ENTRIES + "/" + entryId).header(HttpHeaders.IF_MATCH, etag(ENTRIES + "/" + entryId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
				.andExpect(status().isOk());
	}

	/** Changes only the status of the options of the first slot, in order. */
	private ResultActions patchOptions(String offer, String... statuses) throws Exception {
		DocumentContext json = read(offer);
		StringBuilder options = new StringBuilder();
		for (int i = 0; i < statuses.length; i++) {
			String path = "$.data.composition.slots[0].options[" + i + "]";
			options.append(i == 0 ? "" : ", ").append("{\"optionId\": \"").append(json.<String>read(path + ".optionId"))
					.append("\", \"sourceType\": \"").append(json.<String>read(path + ".sourceType"))
					.append("\", \"status\": \"").append(statuses[i]).append("\"}");
		}
		return patchOffer(offer, "{\"composition\": {\"slots\": [{\"slotId\": \"" + json.read(SLOT_0 + ".slotId")
				+ "\", \"options\": [" + options + "]}]}}");
	}

	/** Points the first option of the first slot to another recipe. */
	private ResultActions changeRecipe(String offer, UUID recipe) throws Exception {
		DocumentContext json = read(offer);
		return patchOffer(offer, "{\"composition\": {\"slots\": [{\"slotId\": \"" + json.read(SLOT_0 + ".slotId")
				+ "\", \"options\": [{\"optionId\": \"" + json.read(OPTION_0 + ".optionId")
				+ "\", \"sourceType\": \"RECIPE\", \"recipeId\": \"" + recipe + "\"}]}]}}");
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
