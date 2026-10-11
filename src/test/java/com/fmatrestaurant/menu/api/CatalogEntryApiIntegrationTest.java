package com.fmatrestaurant.menu.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CatalogEntryService;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * HTTP flows of catalog entries and images against PostgreSQL (Testcontainers). Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class CatalogEntryApiIntegrationTest {

	private static final String ENTRIES = "/api/v1/menu/entries";

	private static final String IMAGES = "/api/v1/media/images";

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private CatalogEntryService entryService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private UUID burgers;

	private UUID drinks;

	private UUID foreign;

	private UUID imageId;

	@BeforeEach
	void setUp() throws Exception {
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
		imageRepository.deleteAll();
		burgers = categoryRepository.save(new Category(1L, "Burgers", null)).getId();
		drinks = categoryRepository.save(new Category(1L, "Drinks", null)).getId();
		foreign = categoryRepository.save(new Category(2L, "Another menu", null)).getId();
		imageId = upload(png(20, 20));
	}

	@Test
	void createsAnInactiveEntryWithItsImageAndCategories() throws Exception {
		String location = createEntry(body("Hawaiian burger", imageId, burgers, burgers, drinks))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-0\""))
				.andExpect(jsonPath("$.data.brandName").value("Hawaiian burger"))
				.andExpect(jsonPath("$.data.description").value("Tasty"))
				.andExpect(jsonPath("$.data.status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.image.id").value(imageId.toString()))
				.andExpect(jsonPath("$.data.image.url").value(endsWith(IMAGES + "/" + imageId)))
				.andExpect(jsonPath("$.data.image.thumbnailUrl").value(endsWith(IMAGES + "/" + imageId)))
				.andExpect(jsonPath("$.data.categoryIds", contains(burgers.toString(), drinks.toString())))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(location, "$.data.id");

		mockMvc.perform(get(ENTRIES + "/" + id))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-0\""))
				.andExpect(jsonPath("$.data.status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.categoryIds", hasSize(2)));
		mockMvc.perform(get(IMAGES + "/" + imageId))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	void createRejectsInvalidDataWithoutCreatingTheEntry() throws Exception {
		createEntry(body("Burger", imageId, burgers, foreign))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/categoryIds"));
		createEntry(body("Burger", imageId, UUID.randomUUID()))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/categoryIds"));
		createEntry(body("Burger", UUID.randomUUID(), burgers))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/imageId"));
		createEntry("{\"brandName\": \"Burger\", \"description\": \"x\", \"categoryIds\": []}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/imageId"));
		createEntry("{\"brandName\": \"Burger\", \"description\": \"x\", \"categoryIds\": [], \"imageId\": \"abc\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/imageId"))
				.andExpect(jsonPath("$.error.details.violations[0].message").value("The value must be a valid UUID"));
		createEntry("{\"brandName\": \"Burger\", \"description\": \"x\", \"categoryIds\": \"abc\", \"imageId\": \""
				+ imageId + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/categoryIds"));
		createEntry("{\"brandName\": \"Burger\", \"description\": \"x\", \"categoryIds\": [\"abc\"], \"imageId\": \""
				+ imageId + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/categoryIds/0"));
		createEntry(body(" ", imageId, burgers))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/brandName"));
		createEntry("{\"brandName\": ")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.message").exists());

		assertEquals(0, entryRepository.count());
	}

	@Test
	void uploadRejectsInvalidImagesWithoutStoringThem() throws Exception {
		byte[] png = png(10, 10);
		byte[][] invalid = {
			"not an image".getBytes(),
			Arrays.copyOf(png, png.length / 2),
			png(Image.MAX_DIMENSION + 1, 1),
			Arrays.copyOf(png, Image.MAX_BYTES + 1),
		};
		for (byte[] bytes : invalid) {
			uploadFile(new MockMultipartFile("file", "image.png", MediaType.IMAGE_PNG_VALUE, bytes))
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath(VIOLATION_PATH).value("/file"));
		}
		uploadFile(new MockMultipartFile("file", "image.jpg", MediaType.IMAGE_JPEG_VALUE, png))
				.andExpect(status().isUnprocessableContent());
		mockMvc.perform(multipart(IMAGES))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/file"));

		assertEquals(1, imageRepository.count());
	}

	@Test
	void patchKeepsOmittedFieldsAndReplacesCategoriesAndImage() throws Exception {
		String id = create("Burger", burgers);
		UUID newImage = upload(png(30, 30));

		patchEntry(id, "W/\"rev-0\"", "{\"description\": \"New\", \"categoryIds\": [\"" + drinks + "\"]}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-1\""))
				.andExpect(jsonPath("$.data.brandName").value("Burger"))
				.andExpect(jsonPath("$.data.description").value("New"))
				.andExpect(jsonPath("$.data.status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.image.id").value(imageId.toString()))
				.andExpect(jsonPath("$.data.categoryIds", contains(drinks.toString())));
		patchEntry(id, "W/\"rev-1\"", "{\"imageId\": \"" + newImage + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.image.id").value(newImage.toString()))
				.andExpect(jsonPath("$.data.description").value("New"))
				.andExpect(jsonPath("$.data.categoryIds", contains(drinks.toString())));
		patchEntry(id, "W/\"rev-2\"", "{\"categoryIds\": []}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.categoryIds", hasSize(0)));
	}

	@Test
	void rejectedPatchLeavesTheEntryUnchanged() throws Exception {
		String id = create("Burger", burgers);

		patchEntry(id, "W/\"rev-0\"", "{\"brandName\": \"New\", \"imageId\": \"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/imageId"));
		patchEntry(id, "W/\"rev-0\"", "{\"brandName\": \"New\", \"categoryIds\": [\"" + foreign + "\"]}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/categoryIds"));
		patchEntry(id, "W/\"rev-0\"", "{\"brandName\": \"New\", \"status\": \"DELETED\"}")
				.andExpect(status().isUnprocessableContent());

		mockMvc.perform(get(ENTRIES + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-0\""))
				.andExpect(jsonPath("$.data.brandName").value("Burger"))
				.andExpect(jsonPath("$.data.image.id").value(imageId.toString()))
				.andExpect(jsonPath("$.data.categoryIds", contains(burgers.toString())));
	}

	@Test
	void statusTransitions() throws Exception {
		String id = create("Burger", burgers);

		changeStatus(id, 0, "ACTIVE").andExpect(status().isOk());
		changeStatus(id, 1, "ARCHIVED").andExpect(status().isOk());
		changeStatus(id, 2, "ACTIVE")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/status"));
		changeStatus(id, 2, "INACTIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		changeStatus(id, 3, "ACTIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		changeStatus(id, 4, "INACTIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		changeStatus(id, 5, "ARCHIVED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ARCHIVED"));
	}

	@Test
	void concurrentChangesNeedTheCurrentEtag() throws Exception {
		String id = create("Burger", burgers);

		patchEntry(id, "W/\"rev-0\"", "{\"brandName\": \"First\"}").andExpect(status().isOk());
		patchEntry(id, "W/\"rev-0\"", "{\"brandName\": \"Second\"}")
				.andExpect(status().isPreconditionFailed())
				.andExpect(jsonPath("$.error.message").exists());
		patchEntry(id, "\"something else\"", "{\"brandName\": \"Third\"}")
				.andExpect(status().isPreconditionFailed());
		mockMvc.perform(patch(ENTRIES + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"brandName\": \"Fourth\"}"))
				.andExpect(status().isPreconditionRequired());

		mockMvc.perform(get(ENTRIES + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-1\""))
				.andExpect(jsonPath("$.data.brandName").value("First"));
	}

	/**
	 * Two requests read rev-0 at the same time: both pass the ETag check, the first commits rev-1 and the
	 * second is stopped by the {@code @Version} column when it writes.
	 */
	@Test
	void aConcurrentWriteThatPassedTheEtagCheckFailsWith412() throws Exception {
		UUID id = UUID.fromString(create("Burger", burgers));
		TransactionTemplate slowRequest = new TransactionTemplate(transactionManager);
		TransactionTemplate fastRequest = new TransactionTemplate(transactionManager);
		fastRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		// MockMvc runs in this thread, so the PATCH joins the slow transaction, which already read rev-0.
		slowRequest.executeWithoutResult(transaction -> {
			entryRepository.findById(id).orElseThrow();
			fastRequest.executeWithoutResult(_ -> entryService.update(id, 0, "First", null, null, null, null));
			try {
				patchEntry(id.toString(), "W/\"rev-0\"", "{\"brandName\": \"Second\"}")
						.andExpect(status().isPreconditionFailed())
						.andExpect(jsonPath("$.error.message").exists());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			transaction.setRollbackOnly();
		});

		mockMvc.perform(get(ENTRIES + "/" + id))
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-1\""))
				.andExpect(jsonPath("$.data.brandName").value("First"));
	}

	@Test
	void patchWithoutRealChangesKeepsTheVersion() throws Exception {
		String id = create("Burger", burgers);

		patchEntry(id, "W/\"rev-0\"", "{\"status\": \"INACTIVE\", \"categoryIds\": [\"" + burgers + "\", \""
				+ burgers + "\"]}")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "W/\"rev-0\""))
				.andExpect(jsonPath("$.data.categoryIds", contains(burgers.toString())));
	}

	@Test
	void conditionalReads() throws Exception {
		String id = create("Burger", burgers);

		mockMvc.perform(get(ENTRIES + "/" + id).header(HttpHeaders.IF_NONE_MATCH, "W/\"rev-0\""))
				.andExpect(status().isNotModified());
		String listEtag = mockMvc.perform(get(ENTRIES))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		mockMvc.perform(get(ENTRIES).header(HttpHeaders.IF_NONE_MATCH, listEtag))
				.andExpect(status().isNotModified());

		changeStatus(id, 0, "ACTIVE").andExpect(status().isOk());

		mockMvc.perform(get(ENTRIES + "/" + id).header(HttpHeaders.IF_NONE_MATCH, "W/\"rev-0\""))
				.andExpect(status().isOk());
		mockMvc.perform(get(ENTRIES).header(HttpHeaders.IF_NONE_MATCH, listEtag))
				.andExpect(status().isOk());
	}

	@Test
	void unknownEntriesAndImagesAreNotFound() throws Exception {
		mockMvc.perform(get(ENTRIES + "/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").exists());
		mockMvc.perform(get(ENTRIES + "/not-a-uuid")).andExpect(status().isNotFound());
		patchEntry(UUID.randomUUID().toString(), "W/\"rev-0\"", "{}").andExpect(status().isNotFound());
		mockMvc.perform(get(IMAGES + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void listFiltersAndPaginatesForAdministration() throws Exception {
		create("Hawaiian burger", burgers);
		create("Lemonade", drinks);
		createEntry(body("Mystery", imageId).replace("Tasty", "Secret recipe")).andExpect(status().isCreated());
		String archived = create("Old burger", burgers);
		changeStatus(archived, 0, "ARCHIVED").andExpect(status().isOk());

		mockMvc.perform(get(ENTRIES))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].brandName", contains("Hawaiian burger", "Lemonade", "Mystery")))
				.andExpect(jsonPath("$.data[0].categories[0].id").value(burgers.toString()))
				.andExpect(jsonPath("$.data[0].categories[0].name").value("Burgers"))
				.andExpect(jsonPath("$.data[0].offerCount").value(0))
				.andExpect(jsonPath("$.data[0].image.id").value(imageId.toString()))
				.andExpect(jsonPath("$.meta.page").value(1))
				.andExpect(jsonPath("$.meta.pageSize").value(12))
				.andExpect(jsonPath("$.meta.total").value(3))
				.andExpect(jsonPath("$.meta.totalPages").value(1));
		expectNames(get(ENTRIES).param("q", "DRINK"), "Lemonade");
		expectNames(get(ENTRIES).param("q", "secret"), "Mystery");
		expectNames(get(ENTRIES).param("q", "burger"), "Hawaiian burger");
		expectNames(get(ENTRIES).param("q", "%"));
		expectNames(get(ENTRIES).param("categoryId", burgers.toString()), "Hawaiian burger");
		expectNames(get(ENTRIES).param("categoryId", "__uncategorized__"), "Mystery");
		expectNames(get(ENTRIES).param("status", "ARCHIVED"), "Old burger");
		expectNames(get(ENTRIES).param("status", "INACTIVE"), "Hawaiian burger", "Lemonade", "Mystery");
		expectNames(get(ENTRIES).param("status", "ACTIVE"));
		mockMvc.perform(get(ENTRIES).param("page", "2").param("pageSize", "2"))
				.andExpect(jsonPath("$.data[*].brandName", contains("Mystery")))
				.andExpect(jsonPath("$.meta.page").value(2))
				.andExpect(jsonPath("$.meta.total").value(3))
				.andExpect(jsonPath("$.meta.totalPages").value(2));
		mockMvc.perform(get(ENTRIES).param("page", "0"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("page"));
		mockMvc.perform(get(ENTRIES).param("categoryId", "bad"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("categoryId"));
		mockMvc.perform(get(ENTRIES).param("status", "BAD"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("status"));
	}

	private void expectNames(RequestBuilder request, String... names)
			throws Exception {
		mockMvc.perform(request)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].brandName", containsInAnyOrder((Object[]) names)))
				.andExpect(jsonPath("$.meta.total").value(names.length));
	}

	private String create(String brandName, UUID category) throws Exception {
		String response = createEntry(body(brandName, imageId, category))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

	private ResultActions createEntry(String json) throws Exception {
		return mockMvc.perform(post(ENTRIES).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions patchEntry(String id, String ifMatch, String json) throws Exception {
		return mockMvc.perform(patch(ENTRIES + "/" + id).header(HttpHeaders.IF_MATCH, ifMatch)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions changeStatus(String id, int version, String status) throws Exception {
		return patchEntry(id, "W/\"rev-" + version + "\"", "{\"status\": \"" + status + "\"}");
	}

	private UUID upload(byte[] png) throws Exception {
		String response = uploadFile(new MockMultipartFile("file", "image.png", MediaType.IMAGE_PNG_VALUE, png))
				.andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.ETAG))
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.data.id"));
	}

	private ResultActions uploadFile(MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart(IMAGES).file(file));
	}

	/** The client sends ACTIVE, but every new entry must start INACTIVE. */
	private static String body(String brandName, UUID image, UUID... categoryIds) {
		String ids = Arrays.stream(categoryIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(", "));
		return """
				{"brandName": "%s", "description": "Tasty", "status": "ACTIVE", "imageId": "%s", "categoryIds": [%s]}
				""".formatted(brandName, image, ids);
	}

	private static byte[] png(int width, int height) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
