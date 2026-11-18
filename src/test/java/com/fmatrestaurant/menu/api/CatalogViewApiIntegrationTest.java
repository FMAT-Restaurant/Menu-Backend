package com.fmatrestaurant.menu.api;

import static com.fmatrestaurant.menu.TestTokens.bearer;
import static com.fmatrestaurant.menu.api.ContractValidation.conformsToContract;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.springframework.test.web.servlet.ResultActions;

import com.fmatrestaurant.menu.AvailabilityEvents;
import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.RabbitMqTestcontainersConfiguration;
import com.fmatrestaurant.menu.TestSecurityConfiguration;
import com.fmatrestaurant.menu.TestTokens;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
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
 * BE-14: the visible catalog, {@code GET /menu/catalog} and {@code GET /menu/catalog/categories}, as whoever
 * consults it with TK-CATALOGO ({@code menu:catalog:read}) sees it. Every catalog response is validated against the
 * contract. Requires Docker.
 */
@Import({ TestcontainersConfiguration.class, RabbitMqTestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.rabbitmq.listener.simple.auto-startup=true" })
@AutoConfigureMockMvc
class CatalogViewApiIntegrationTest {

	private static final String CATALOG = "/api/v1/menu/catalog";

	private static final String CATEGORIES = CATALOG + "/categories";

	private static final String ENTRIES = "/api/v1/menu/entries";

	private static final String OFFERS = "/api/v1/menu/offers";

	/** INV-CARNE. */
	private static final InventoryItem BEEF = new InventoryItem("CARNE-150G", "Carne 150 g", "g");

	/** INV-REFRESCO. */
	private static final InventoryItem SODA = new InventoryItem("REFRESCO-355", "Refresco 355 ml", "ml");

	/** INV-PAN. */
	private static final InventoryItem BUN = new InventoryItem("PAN-BRIOCHE", "Pan brioche", "pieza");

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

	/** REC-DISP. */
	private UUID recDisp;

	/** REC-NODISP. */
	private UUID recNoDisp;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws Exception {
		InventoryStub.INSTANCE.reset();
		InventoryStub.INSTANCE.add(BEEF);
		InventoryStub.INSTANCE.add(SODA);
		InventoryStub.INSTANCE.add(BUN);
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
		imageRepository.deleteAll();
		events = new AvailabilityEvents(rabbitTemplate, processedEvents, routingKey);
		imageId = imageRepository.save(new Image("image/png", png())).getId();
		recDisp = recipe("Hamburguesa");
		events.publish(recDisp, true);
		recNoDisp = recipe("Pastel");
		events.publish(recNoDisp, false);
	}

	// ---------- RF-CONS-01: visibilidad ----------

	@Test
	@DisplayName("TC-CONS-01-CA1 — Solo se presentan entradas, ofertas y opciones activas")
	void onlyActiveElements() throws Exception {
		String active = activeEntry("CONS Activa");
		activeOffer(active, "Activa", slot("Plato", null, recipeOption("Opción activa", recDisp, "ACTIVE"),
				recipeOption("Opción inactiva", recDisp, "INACTIVE")));
		offer(active, "Inactiva", "100", slot("Plato", null, recipeOption("Otra", recDisp, "ACTIVE")));
		entry("CONS Inactiva");
		String archived = activeEntry("CONS Archivada");
		entryStatus(archived, "ARCHIVED");

		DocumentContext page = catalog().andExpect(header().exists(HttpHeaders.ETAG)).andReturn().getResponse()
				.getContentAsString().transform(JsonPath::parse);

		assertEquals(List.of("CONS Activa"), page.read("$.data[*].brandName"));
		assertEquals(List.of("Activa"), page.read("$.data[0].offers[*].presentationTag"));
		assertEquals(List.of("Opción activa"), page.read("$.data[0].offers[0].composition.slots[0].options[*].displayName"));
	}

	@Test
	@DisplayName("TC-CONS-01-CA2 — Lo no disponible se presenta deshabilitado")
	void notAvailableIsPresentedDisabled() throws Exception {
		activeOffer(activeEntry("CONS No disponible"), "Individual",
				slot("Plato", null, recipeOption("Pastel", recNoDisp, "ACTIVE")));
		activeOffer(activeEntry("CONS Disponible"), "Individual",
				slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));

		DocumentContext page = page(catalog());

		for (Map.Entry<String, Boolean> expected : Map.of("CONS No disponible", false, "CONS Disponible", true)
				.entrySet()) {
			String entry = "$.data[?(@.brandName == '" + expected.getKey() + "')]";
			List<Boolean> values = List.of(expected.getValue());
			assertEquals(values, page.read(entry + ".isAvailable"), expected.getKey());
			assertEquals(values, page.read(entry + ".offers[0].isAvailable"), expected.getKey());
			assertEquals(values, page.read(entry + ".offers[0].composition.slots[0].isAvailable"), expected.getKey());
			assertEquals(values, page.read(entry + ".offers[0].composition.slots[0].options[0].isAvailable"),
					expected.getKey());
		}
	}

	@Test
	@DisplayName("TC-CONS-01-CA3 — Cada grupo muestra su tiempo de servicio")
	void everySlotShowsItsCourse() throws Exception {
		activeOffer(activeEntry("CONS Combo"), "Combo",
				slot("Plato principal", "plato fuerte", recipeOption("Hamburguesa", recDisp, "ACTIVE")),
				slot("Bebida", "bebida", inventoryOption("Refresco", SODA, "355", "ACTIVE")));

		catalog().andExpect(jsonPath("$.data[0].offers[0].composition.slots", hasSize(2)))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].name").value("Plato principal"))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].course").value("plato fuerte"))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[1].name").value("Bebida"))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[1].course").value("bebida"));
	}

	@Test
	@DisplayName("TC-CONS-01-CA4 — Se presentan todas las categorías y la agrupación «sin categoría»")
	void everyCategoryAndTheUncategorizedGroup() throws Exception {
		UUID burgers = category("Hamburguesas");
		category("Postres");
		activeEntry("CONS Clásica", burgers);
		activeEntry("CONS Suelta");
		entry("CONS Inactiva", burgers);

		mockMvc.perform(get(CATEGORIES).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG)))
				.andExpect(status().isOk()).andExpect(conformsToContract())
				.andExpect(header().exists(HttpHeaders.ETAG))
				.andExpect(jsonPath("$.data[*].name", contains("Hamburguesas", "Postres")))
				.andExpect(jsonPath("$.data[0].entryCount").value(1))
				.andExpect(jsonPath("$.data[1].entryCount").value(0))
				.andExpect(jsonPath("$.uncategorized.entryCount").value(1));
	}

	@Test
	@DisplayName("TC-CONS-01-CA5 — La consulta del catálogo requiere «Ver catálogo»")
	void theCatalogRequiresItsPermission() throws Exception {
		for (String path : List.of(CATALOG, CATEGORIES)) {
			for (String token : List.of(TestTokens.NONE, TestTokens.with("menu:entries:read"))) {
				mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
						.andExpect(status().isForbidden()).andExpect(conformsToContract())
						.andExpect(jsonPath("$.error.message").exists());
			}
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG)))
					.andExpect(status().isOk());
		}
	}

	// ---------- RF-CONS-02: ofertas vendibles ----------

	@Test
	@DisplayName("TC-CONS-02-CA1/CA2/CA3/CA5 — Cada oferta es un producto independiente, sin preselección y con su precio")
	void everyOfferIsSoldOnItsOwn() throws Exception {
		String entry = activeEntry("CONS Hamburguesa Hawaiana");
		pricedOffer(entry, "Individual", "180.0", slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));
		pricedOffer(entry, "Doble", "240.0", slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));

		DocumentContext page = page(catalog());

		assertEquals("CONS Hamburguesa Hawaiana", page.read("$.data[0].brandName"));
		assertEquals(Set.of("id", "brandName", "description", "image", "categoryIds", "isAvailable", "offers"),
				page.<Map<String, Object>>read("$.data[0]").keySet());
		// Alphabetical order by presentation tag.
		assertEquals(List.of("Doble", "Individual"), page.read("$.data[0].offers[*].presentationTag"));
		assertEquals(List.of(240.0, 180.0), page.<List<Number>>read("$.data[0].offers[*].basePrice").stream()
				.map(Number::doubleValue).toList());
		List<Map<String, Object>> offers = page.read("$.data[0].offers");
		for (Map<String, Object> offer : offers) {
			assertEquals(Set.of("id", "presentationTag", "basePrice", "image", "isAvailable", "composition"),
					offer.keySet());
		}
		assertNotEquals(offers.get(0).get("id"), offers.get(1).get("id"));
	}

	@Test
	@DisplayName("TC-CONS-02-CA4 — La etiqueta no sustituye la composición ni fija cantidades")
	void theTagDoesNotReplaceTheComposition() throws Exception {
		activeOffer(activeEntry("CONS Tacos"), "Orden de 3",
				slot("Tacos", "plato fuerte", recipeOption("Taco", recDisp, "ACTIVE")));

		catalog().andExpect(jsonPath("$.data[0].offers[0].presentationTag").value("Orden de 3"))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].name").value("Tacos"))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].quantity").value(1));
	}

	// ---------- RF-CONS-03: búsqueda y filtro ----------

	@Test
	@DisplayName("TC-CONS-03-CA1 — La búsqueda considera nombre comercial, descripción y categorías")
	void searchByNameDescriptionAndCategory() throws Exception {
		UUID zafiro = category("Línea Zafiro");
		activeEntry("Zafiro Burger");
		activeEntry("CONS Malteada", "Receta Zafiro de la casa");
		activeEntry("CONS Ensalada", zafiro);
		activeEntry("CONS Agua");

		catalog("q", "Zafiro").andExpect(jsonPath("$.data[*].brandName",
				containsInAnyOrder("Zafiro Burger", "CONS Malteada", "CONS Ensalada")));
	}

	@Test
	@DisplayName("TC-CONS-03-CA2a/CA2b — Filtro por una categoría y por «sin categoría»")
	void filterByCategory() throws Exception {
		UUID drinks = category("Bebidas");
		UUID desserts = category("Postres");
		activeEntry("CONS Limonada", drinks);
		activeEntry("CONS Pastel", desserts);
		activeEntry("CONS Agua");

		catalog("categoryId", drinks.toString()).andExpect(jsonPath("$.data[*].brandName", contains("CONS Limonada")))
				.andExpect(jsonPath("$.data[0].categoryIds", contains(drinks.toString())));
		catalog("categoryId", "__uncategorized__").andExpect(jsonPath("$.data[*].brandName", contains("CONS Agua")))
				.andExpect(jsonPath("$.data[0].categoryIds", empty()));
		catalog("categoryId", UUID.randomUUID().toString()).andExpect(jsonPath("$.data", empty()))
				.andExpect(jsonPath("$.meta.total").value(0));
	}

	@Test
	@DisplayName("TC-CONS-03-CA4a — Los resultados con «Ver catálogo» respetan la visibilidad de RF-CONS-01")
	void searchRespectsTheVisibility() throws Exception {
		activeEntry("Rubí Activa");
		entryStatus(activeEntry("Rubí Inactiva"), "INACTIVE");
		entryStatus(activeEntry("Rubí Archivada"), "ARCHIVED");

		catalog("q", "Rubí").andExpect(jsonPath("$.data[*].brandName", contains("Rubí Activa")));
	}

	// ---------- Composición ----------

	@Test
	@DisplayName("TC-COMP-02-CA2/TC-COMP-03-CA2 — Todo grupo participa con sus rondas")
	void everySlotWithItsRounds() throws Exception {
		activeOffer(activeEntry("CONS Combo"), "Combo",
				slot("Plato principal", null, 3, recipeOption("Hamburguesa", recDisp, "ACTIVE")),
				slot("Bebida", null, 1, inventoryOption("Refresco", SODA, "355", "ACTIVE")));

		catalog().andExpect(jsonPath("$.data[0].offers[0].composition.slots[*].name",
				contains("Plato principal", "Bebida")))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[*].quantity", contains(3, 1)))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].options", hasSize(1)))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[1].options", hasSize(1)));
	}

	@Test
	@DisplayName("TC-COMP-04-CA3 — El tiempo de servicio se presenta asociado al grupo")
	void theCourseBelongsToItsSlot() throws Exception {
		activeOffer(activeEntry("CONS Hamburguesa"), "Individual",
				slot("Plato principal", "plato fuerte", recipeOption("Hamburguesa", recDisp, "ACTIVE")),
				slot("Postre", "postre", inventoryOption("Pan", BUN, "1", "ACTIVE")));

		catalog().andExpect(jsonPath("$.data[0].offers[0].composition.slots[*].course",
				contains("plato fuerte", "postre")));
	}

	@Test
	@DisplayName("TC-COMP-05-CA2a/CA2b — Las opciones inactivas no se presentan y las no disponibles van deshabilitadas")
	void inactiveAndNotAvailableOptions() throws Exception {
		activeOffer(activeEntry("CONS Hamburguesa"), "Individual", slot("Plato", null,
				recipeOption("Disponible", recDisp, "ACTIVE"), inventoryOption("Inactiva", BEEF, "150", "INACTIVE"),
				recipeOption("No disponible", recNoDisp, "ACTIVE")));

		DocumentContext page = page(catalog());

		String options = "$.data[0].offers[0].composition.slots[0].options";
		assertEquals(List.of("Disponible", "No disponible"), page.read(options + "[*].displayName"));
		assertEquals(List.of(true, false), page.read(options + "[*].isAvailable"));
	}

	@Test
	@DisplayName("TC-COMP-06-CA2 — Con varias opciones elegibles no hay predeterminada")
	void noDefaultOption() throws Exception {
		String entry = activeEntry("CONS Hamburguesa");
		String offer = offer(entry, "Individual", "180", slot("Plato principal", null,
				recipeOption("Hamburguesa", recDisp, "ACTIVE"), inventoryOption("OPC-INV-DISP", BEEF, "150", "ACTIVE")));
		events.publish(UUID.fromString(read(OFFERS + "/" + offer).read("$.data.composition.slots[0].options[1].optionId")),
				true);
		offerStatus(offer, "ACTIVE");

		DocumentContext page = page(catalog());

		String slot = "$.data[0].offers[0].composition.slots[0]";
		assertEquals(Set.of("slotId", "name", "quantity", "isAvailable", "options"),
				page.<Map<String, Object>>read(slot).keySet());
		assertEquals(List.of(true, true), page.read(slot + ".options[*].isAvailable"));
		List<Map<String, Object>> options = page.read(slot + ".options");
		for (Map<String, Object> option : options) {
			assertEquals(Set.of("optionId", "displayName", "isAvailable"), option.keySet());
		}
	}

	@Test
	@DisplayName("TC-COMP-07-CA1/CA2 — Grupos y opciones en el orden del administrador")
	void theOrderOfTheAdministrator() throws Exception {
		String offer = offer(activeEntry("CONS Combo"), "Combo", "200",
				slot("Bebida", null, inventoryOption("Agua", SODA, "355", "ACTIVE")),
				slot("Plato principal", null, inventoryOption("Zanahoria", BUN, "1", "ACTIVE"),
						inventoryOption("Agua fresca", SODA, "355", "ACTIVE"), recipeOption("Mixta", recDisp, "ACTIVE")),
				slot("Postre", null, inventoryOption("Pan", BUN, "1", "ACTIVE")));
		DocumentContext created = read(OFFERS + "/" + offer);
		List<String> slots = created.read("$.data.composition.slots[*].slotId");
		List<String> options = created.read("$.data.composition.slots[1].options[*].optionId");
		offerStatus(offer, "ACTIVE");

		patchOffer(offer, "{\"composition\": {\"slots\": [{\"slotId\": \"" + slots.get(2) + "\"}, "
				+ "{\"slotId\": \"" + slots.get(0) + "\"}, {\"slotId\": \"" + slots.get(1) + "\", \"options\": ["
				+ "{\"optionId\": \"" + options.get(2) + "\", \"sourceType\": \"RECIPE\"}, "
				+ "{\"optionId\": \"" + options.get(0) + "\", \"sourceType\": \"INVENTORY_ITEM\"}, "
				+ "{\"optionId\": \"" + options.get(1) + "\", \"sourceType\": \"INVENTORY_ITEM\"}]}]}}")
				.andExpect(status().isOk());

		catalog().andExpect(jsonPath("$.data[0].offers[0].composition.slots[*].name",
				contains("Postre", "Bebida", "Plato principal")))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[*].slotId",
						contains(slots.get(2), slots.get(0), slots.get(1))))
				.andExpect(jsonPath("$.data[0].offers[0].composition.slots[2].options[*].displayName",
						contains("Mixta", "Zanahoria", "Agua fresca")));
	}

	// ---------- Efecto de cambios administrativos ----------

	@Test
	@DisplayName("TC-CAT-03-CA3 — La entrada que pierde su única categoría pasa a «sin categoría»")
	void anEntryWithoutItsOnlyCategoryIsUncategorized() throws Exception {
		UUID category = category("Temporada");
		String entry = activeEntry("CONS Temporada", category);
		long before = page(categories()).<Number>read("$.uncategorized.entryCount").longValue();

		mockMvc.perform(delete("/api/v1/menu/categories/" + category)
				.header(HttpHeaders.IF_MATCH, etag("/api/v1/menu/categories/" + category)))
				.andExpect(status().isNoContent());

		assertEquals(before + 1, page(categories()).<Number>read("$.uncategorized.entryCount").longValue());
		catalog("categoryId", "__uncategorized__").andExpect(jsonPath("$.data[*].id", contains(entry)));
	}

	@Test
	@DisplayName("TC-ENT-04-CA1 — Una entrada inactivada deja de presentarse en el catálogo")
	void anInactivatedEntryIsNotPresented() throws Exception {
		String entry = activeEntry("Hamburguesa Hawaiana");
		catalog().andExpect(jsonPath("$.data[*].id", contains(entry)));

		entryStatus(entry, "INACTIVE");

		catalog().andExpect(jsonPath("$.data", empty()));
	}

	@Test
	@DisplayName("TC-OFE-05-CA1 — Una oferta inactivada deja de presentarse en el catálogo")
	void anInactivatedOfferIsNotPresented() throws Exception {
		String entry = activeEntry("CONS Hamburguesa");
		String offer = activeOffer(entry, "Individual", slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));
		catalog().andExpect(jsonPath("$.data[0].offers[*].id", contains(offer)));

		patchOffer(offer, "{\"status\": \"INACTIVE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));

		catalog().andExpect(jsonPath("$.data[0].offers", empty()))
				.andExpect(jsonPath("$.data[0].isAvailable").value(false));
	}

	@Test
	@DisplayName("TC-OPC-05-CA1 — Una opción inactivada deja de presentarse en el catálogo")
	void anInactivatedOptionIsNotPresented() throws Exception {
		String offer = activeOffer(activeEntry("CONS Hamburguesa"), "Individual", slot("Plato", null,
				inventoryOption("Carne", BEEF, "150", "ACTIVE"), recipeOption("Hamburguesa", recDisp, "ACTIVE")));
		DocumentContext before = read(OFFERS + "/" + offer);
		String slot = before.read("$.data.composition.slots[0].slotId");
		String optionA = before.read("$.data.composition.slots[0].options[0].optionId");
		String optionR = before.read("$.data.composition.slots[0].options[1].optionId");
		catalog().andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].options[*].optionId",
				contains(optionA, optionR)));

		patchOffer(offer, "{\"composition\": {\"slots\": [{\"slotId\": \"" + slot + "\", \"options\": ["
				+ "{\"optionId\": \"" + optionA + "\", \"sourceType\": \"INVENTORY_ITEM\"}, "
				+ "{\"optionId\": \"" + optionR + "\", \"sourceType\": \"RECIPE\", \"status\": \"INACTIVE\"}]}]}}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.composition.slots[0].options[1].status").value("INACTIVE"));

		catalog().andExpect(jsonPath("$.data[0].offers[0].composition.slots[0].options[*].optionId",
				contains(optionA)));
	}

	@Test
	@DisplayName("TC-DISP-05-CA1a — Oferta ordenable")
	void anOrderableOffer() throws Exception {
		String entry = activeEntry("CONS Hamburguesa");
		String offer = activeOffer(entry, "Individual", slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(true));
		mockMvc.perform(get(ENTRIES + "/" + entry)).andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.isAvailable").value(true));
		catalog().andExpect(jsonPath("$.data[0].isAvailable").value(true))
				.andExpect(jsonPath("$.data[0].offers[0].isAvailable").value(true));
	}

	@Test
	@DisplayName("TC-DISP-05-CA2 — Las ofertas de una entrada archivada no son ordenables")
	void offersOfAnArchivedEntryAreNotOrderable() throws Exception {
		String entry = activeEntry("CONS Hamburguesa");
		activeOffer(entry, "Individual", slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));

		mockMvc.perform(patch(ENTRIES + "/" + entry).header(HttpHeaders.IF_MATCH, etag(ENTRIES + "/" + entry))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ARCHIVED\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));

		catalog().andExpect(jsonPath("$.data", empty()));
	}

	// ---------- Otros ----------

	@Test
	@DisplayName("Una entrada ACTIVE sin ofertas ACTIVE aparece con offers vacío e isAvailable false")
	void anActiveEntryWithoutActiveOffers() throws Exception {
		activeEntry("Agua de Jamaica");

		catalog().andExpect(jsonPath("$.data[0].offers", empty()))
				.andExpect(jsonPath("$.data[0].isAvailable").value(false));
	}

	@Test
	@DisplayName("El catálogo y sus categorías responden 304 a un If-None-Match vigente, y no después de un cambio")
	void conditionalReads() throws Exception {
		String entry = activeEntry("CONS Hamburguesa");
		activeOffer(entry, "Individual", slot("Plato", null, recipeOption("Hamburguesa", recDisp, "ACTIVE")));
		String catalogEtag = catalog().andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		String categoriesEtag = categories().andReturn().getResponse().getHeader(HttpHeaders.ETAG);

		for (Map.Entry<String, String> etag : Map.of(CATALOG, catalogEtag, CATEGORIES, categoriesEtag).entrySet()) {
			mockMvc.perform(get(etag.getKey()).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG))
					.header(HttpHeaders.IF_NONE_MATCH, etag.getValue())).andExpect(status().isNotModified());
		}

		events.publish(recDisp, false);

		mockMvc.perform(get(CATALOG).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG))
				.header(HttpHeaders.IF_NONE_MATCH, catalogEtag)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].isAvailable").value(false));
	}

	@Test
	@DisplayName("El catálogo pagina en orden alfabético por nombre comercial")
	void pagination() throws Exception {
		for (String name : List.of("Cc", "Aa", "Bb")) {
			activeEntry(name);
		}

		catalog("pageSize", "2").andExpect(jsonPath("$.data[*].brandName", contains("Aa", "Bb")))
				.andExpect(jsonPath("$.meta.total").value(3)).andExpect(jsonPath("$.meta.totalPages").value(2));
		catalog("pageSize", "2", "page", "2").andExpect(jsonPath("$.data[*].brandName", contains("Cc")));
	}

	// ---------- Ayudantes ----------

	/** GET of the catalog with TK-CATALOGO, validated against the contract. */
	private ResultActions catalog(String... params) throws Exception {
		var request = get(CATALOG).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG));
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mockMvc.perform(request).andExpect(status().isOk()).andExpect(conformsToContract());
	}

	private ResultActions categories() throws Exception {
		return mockMvc.perform(get(CATEGORIES).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG)))
				.andExpect(status().isOk()).andExpect(conformsToContract());
	}

	private static DocumentContext page(ResultActions result) throws Exception {
		return JsonPath.parse(result.andReturn().getResponse().getContentAsString());
	}

	private DocumentContext read(String path) throws Exception {
		return page(mockMvc.perform(get(path)));
	}

	private String etag(String path) throws Exception {
		return mockMvc.perform(get(path)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private UUID category(String name) {
		return categoryRepository.save(new Category(1L, name, "Categoría " + name)).getId();
	}

	private UUID recipe(String name) throws Exception {
		String json = mockMvc.perform(post("/api/v1/menu/recipes").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"" + name + "\", \"description\": \"Receta\", \"instructions\": \"Preparar\", "
						+ "\"supplies\": [{\"inventoryItemId\": \"" + BEEF.id() + "\", \"quantity\": 150}]}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(json, "$.data.id"));
	}

	private String entry(String name, UUID... categories) throws Exception {
		return entry(name, "Descripción de " + name, categories);
	}

	private String entry(String name, String description, UUID... categories) throws Exception {
		String ids = Arrays.stream(categories).map(id -> "\"" + id + "\"").collect(Collectors.joining(", "));
		String json = mockMvc.perform(post(ENTRIES).header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"brandName\": \"" + name + "\", \"description\": \"" + description + "\", \"imageId\": \""
						+ imageId + "\", \"categoryIds\": [" + ids + "]}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.data.id");
	}

	private String activeEntry(String name, UUID... categories) throws Exception {
		String id = entry(name, categories);
		entryStatus(id, "ACTIVE");
		return id;
	}

	private String activeEntry(String name, String description) throws Exception {
		String id = entry(name, description);
		entryStatus(id, "ACTIVE");
		return id;
	}

	private void entryStatus(String entry, String status) throws Exception {
		mockMvc.perform(patch(ENTRIES + "/" + entry).header(HttpHeaders.IF_MATCH, etag(ENTRIES + "/" + entry))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}"))
				.andExpect(status().isOk());
	}

	private static String recipeOption(String name, UUID recipe, String status) {
		return "{\"displayName\": \"" + name + "\", \"sourceType\": \"RECIPE\", \"recipeId\": \"" + recipe
				+ "\", \"status\": \"" + status + "\"}";
	}

	private static String inventoryOption(String name, InventoryItem item, String quantity, String status) {
		return "{\"displayName\": \"" + name + "\", \"sourceType\": \"INVENTORY_ITEM\", \"inventoryItemId\": \""
				+ item.id() + "\", \"quantity\": " + quantity + ", \"status\": \""
				+ status + "\"}";
	}

	private static String slot(String name, String course, String... options) {
		return slot(name, course, 1, options);
	}

	private static String slot(String name, String course, int quantity, String... options) {
		return "{\"name\": \"" + name + "\", \"quantity\": " + quantity
				+ (course == null ? "" : ", \"course\": \"" + course + "\"") + ", \"options\": ["
				+ String.join(", ", options) + "]}";
	}

	private String offer(String entry, String tag, String price, String... slots) throws Exception {
		String json = mockMvc.perform(post(ENTRIES + "/" + entry + "/offers").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"presentationTag\": \"" + tag + "\", \"basePrice\": " + price + ", \"imageId\": \"" + imageId
						+ "\", \"composition\": {\"slots\": [" + String.join(", ", slots) + "]}}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.data.id");
	}

	private String activeOffer(String entry, String tag, String... slots) throws Exception {
		return pricedOffer(entry, tag, "180", slots);
	}

	private String pricedOffer(String entry, String tag, String price, String... slots) throws Exception {
		String id = offer(entry, tag, price, slots);
		offerStatus(id, "ACTIVE");
		return id;
	}

	private void offerStatus(String offer, String status) throws Exception {
		patchOffer(offer, "{\"status\": \"" + status + "\"}").andExpect(status().isOk());
	}

	private ResultActions patchOffer(String offer, String body) throws Exception {
		return mockMvc.perform(patch(OFFERS + "/" + offer).header("Idempotency-Key", UUID.randomUUID())
				.header(HttpHeaders.IF_MATCH, etag(OFFERS + "/" + offer)).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
