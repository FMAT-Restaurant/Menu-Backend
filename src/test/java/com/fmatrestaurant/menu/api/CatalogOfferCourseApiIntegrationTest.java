package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.ComponentIngredient;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.Recipe;
import com.fmatrestaurant.menu.domain.RecipeRevision;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;
import com.jayway.jsonpath.JsonPath;

/** Optional slot courses over HTTP and PostgreSQL, using recipe options without Inventory calls. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class CatalogOfferCourseApiIntegrationTest {

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final String COURSE = "$.data.composition.slots[0].course";

	@Autowired
	private MockMvc mockMvc;

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

	private UUID recipeId;

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
		entryId = entryRepository.saveAndFlush(new CatalogEntry(1L, "Burger", "Classic", imageId, List.of())).getId();
		Recipe recipe = recipeRepository.saveAndFlush(new Recipe());
		recipeId = recipe.getId();
		revisionRepository.saveAndFlush(new RecipeRevision(recipe, 1, "Burger", "Classic", "Grill and serve",
				List.of(new ComponentIngredient(new InventoryItem("beef", "Beef", "g"), BigDecimal.TEN))));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "\"course\": null," })
	void createsAndListsASlotWithAnOmittedOrNullCourse(String courseField) throws Exception {
		String id = JsonPath.read(create(courseField).andExpect(status().isCreated())
				.andExpect(jsonPath(COURSE).doesNotExist()).andReturn().getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get(OFFERS + "/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath(COURSE).doesNotExist());
		mockMvc.perform(get(OFFERS)).andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.total").value(1))
				.andExpect(jsonPath("$.data[0].slotCount").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "\"course\": null," })
	void patchCanAddASlotWithAnOmittedOrNullCourse(String courseField) throws Exception {
		String id = JsonPath.read(create("\"course\": \"entrada\",").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");

		String newSlot = slot(courseField).replace("\"displayName\"", "\"status\":\"ACTIVE\",\"displayName\"");
		patchOffer(id, "{\"composition\":{\"slots\":[" + newSlot + "]}}")
				.andExpect(status().isOk()).andExpect(jsonPath(COURSE).doesNotExist());
		mockMvc.perform(get(OFFERS + "/" + id)).andExpect(jsonPath(COURSE).doesNotExist());
	}

	@Test
	void patchKeepsAnOmittedCourseAndRemovesAnExplicitNull() throws Exception {
		String json = create("\"course\": \"entrada\",").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(json, "$.data.id");
		String slotId = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		String slotPrefix = "{\"composition\":{\"slots\":[{\"slotId\":\"" + slotId + "\"";

		patchOffer(id, slotPrefix + ",\"name\":\"Renamed\"}]}}")
				.andExpect(status().isOk()).andExpect(jsonPath(COURSE).value("entrada"));
		patchOffer(id, slotPrefix + ",\"course\":null}]}}")
				.andExpect(status().isOk()).andExpect(jsonPath(COURSE).doesNotExist());
		patchOffer(id, slotPrefix + ",\"course\":\"bebida\"}]}}")
				.andExpect(status().isOk()).andExpect(jsonPath(COURSE).value("bebida"));
	}

	@Test
	void invalidCoursesAreStillRejected() throws Exception {
		create("\"course\":\"snack\",").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.details.violations[0].path").value("/composition/slots/0/course"));
		String json = create("").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(json, "$.data.id");
		String slotId = JsonPath.read(json, "$.data.composition.slots[0].slotId");
		patchOffer(id, "{\"composition\":{\"slots\":[{\"slotId\":\"" + slotId + "\",\"course\":\"snack\"}]}}")
				.andExpect(status().isUnprocessableContent());
		mockMvc.perform(get(OFFERS + "/" + id)).andExpect(jsonPath(COURSE).doesNotExist());
	}

	private ResultActions create(String courseField) throws Exception {
		String body = "{\"presentationTag\":\"Individual\",\"basePrice\":10,\"imageId\":\"" + imageId
				+ "\",\"composition\":{\"slots\":[" + slot(courseField) + "]}}";
		return mockMvc.perform(post("/api/v1/menu/entries/" + entryId + "/offers")
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String slot(String courseField) {
		return "{\"name\":\"Main\",\"quantity\":1," + courseField + "\"options\":["
				+ "{\"displayName\":\"Burger\",\"sourceType\":\"RECIPE\",\"recipeId\":\""
				+ recipeId + "\",\"recipeRevision\":1}]}";
	}

	private ResultActions patchOffer(String id, String body) throws Exception {
		String etag = mockMvc.perform(get(OFFERS + "/" + id)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		assertNotNull(etag, "ETag header is missing from GET response");
		return mockMvc.perform(patch(OFFERS + "/" + id).header(HttpHeaders.IF_MATCH, etag)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
