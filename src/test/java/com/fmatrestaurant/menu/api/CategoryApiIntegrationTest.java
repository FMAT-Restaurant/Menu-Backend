package com.fmatrestaurant.menu.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;
import com.jayway.jsonpath.JsonPath;

/** HTTP flows of categories against PostgreSQL (Testcontainers). Requires Docker. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class CategoryApiIntegrationTest {

	private static final String CATEGORIES = "/api/v1/menu/categories";

	private static final UUID IMAGE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final String VIOLATION_PATH = "$.error.details.violations[0].path";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
	}

	@Test
	void createsListsAndPartiallyUpdatesCategoriesWithStrongEtags() throws Exception {
		String response = mockMvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" Desserts \",\"description\":\" Sweet things \"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
				.andExpect(jsonPath("$.data.name").value("Desserts"))
				.andExpect(jsonPath("$.data.description").value("Sweet things"))
				.andExpect(jsonPath("$.data.entryCount").value(0))
				.andExpect(jsonPath("$.data.version").value(0))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(response, "$.data.id");

		String collectionEtag = mockMvc.perform(get(CATEGORIES))
				.andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.ETAG))
				.andExpect(jsonPath("$.data[0].version").value(0))
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
		mockMvc.perform(get(CATEGORIES).header(HttpHeaders.IF_NONE_MATCH, collectionEtag))
				.andExpect(status().isNotModified());
		mockMvc.perform(patch(CATEGORIES + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Missing validator\"}"))
				.andExpect(status().isPreconditionRequired());
		mockMvc.perform(patch(CATEGORIES + "/" + id).header(HttpHeaders.IF_MATCH, "W/\"rev-0\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Weak validator\"}"))
				.andExpect(status().isPreconditionFailed());

		mockMvc.perform(patch(CATEGORIES + "/" + id).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cold desserts\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\""))
				.andExpect(jsonPath("$.data.name").value("Cold desserts"))
				.andExpect(jsonPath("$.data.description").value("Sweet things"))
				.andExpect(jsonPath("$.data.version").value(1));

		mockMvc.perform(patch(CATEGORIES + "/" + id).header(HttpHeaders.IF_MATCH, "\"rev-1\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"rev-2\""))
				.andExpect(jsonPath("$.data.description").doesNotExist());

		mockMvc.perform(patch(CATEGORIES + "/" + id).header(HttpHeaders.IF_MATCH, "\"rev-1\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Stale update\"}"))
				.andExpect(status().isPreconditionFailed());
		mockMvc.perform(get(CATEGORIES))
				.andExpect(jsonPath("$.data[0].name").value("Cold desserts"))
				.andExpect(jsonPath("$.data[0].version").value(2))
				.andExpect(jsonPath("$.data[0].description").doesNotExist());
	}

	@Test
	void listPaginatesCategoriesAndCountsDistinctEntriesWithinTheMenu() throws Exception {
		Category beverages = categoryRepository.saveAndFlush(new Category(1L, "Beverages", null));
		Category food = categoryRepository.saveAndFlush(new Category(1L, "Food", null));
		categoryRepository.saveAndFlush(new Category(2L, "Foreign", null));
		entryRepository.saveAndFlush(new CatalogEntry(1L, "First", "x", IMAGE_ID, List.of(beverages, food)));
		entryRepository.saveAndFlush(new CatalogEntry(1L, "Second", "x", IMAGE_ID, List.of(beverages)));

		mockMvc.perform(get(CATEGORIES).param("page", "1").param("pageSize", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].name", contains("Beverages")))
				.andExpect(jsonPath("$.data[0].entryCount").value(2))
				.andExpect(jsonPath("$.meta.page").value(1))
				.andExpect(jsonPath("$.meta.pageSize").value(1))
				.andExpect(jsonPath("$.meta.total").value(2))
				.andExpect(jsonPath("$.meta.totalPages").value(2));
		mockMvc.perform(get(CATEGORIES).param("page", "2").param("pageSize", "1"))
				.andExpect(jsonPath("$.data[*].name", contains("Food")))
				.andExpect(jsonPath("$.data[0].entryCount").value(1));
		mockMvc.perform(get(CATEGORIES).param("pageSize", "0"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("pageSize"));
	}

	@Test
	void emptyMenuHasAnEmptyCategoryPageAndInvalidCreateDoesNotPersist() throws Exception {
		mockMvc.perform(get(CATEGORIES))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)))
				.andExpect(jsonPath("$.meta.total").value(0))
				.andExpect(jsonPath("$.meta.totalPages").value(0));
		mockMvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" \",\"description\":\"x\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath(VIOLATION_PATH).value("/name"));
		assertEquals(0, categoryRepository.count());
	}

	@Test
	void foreignCategoriesCannotBeUpdatedFromThisMenu() throws Exception {
		Category foreign = categoryRepository.saveAndFlush(new Category(2L, "Foreign", "Original"));

		mockMvc.perform(patch(CATEGORIES + "/" + foreign.getId()).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
				.andExpect(status().isNotFound());
		assertEquals("Foreign", categoryRepository.findById(foreign.getId()).orElseThrow().getName());
	}

	@Test
	void concurrentUpdateThatReadTheSameVersionFailsWithoutOverwritingTheNewerValue() throws Exception {
		Category category = categoryRepository.saveAndFlush(new Category(1L, "Desserts", "Old"));
		TransactionTemplate slowRequest = new TransactionTemplate(transactionManager);
		TransactionTemplate fastRequest = new TransactionTemplate(transactionManager);
		fastRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		slowRequest.executeWithoutResult(transaction -> {
			categoryRepository.findById(category.getId()).orElseThrow();
			fastRequest.executeWithoutResult(_ -> categoryService.update(category.getId(), 0, "First", true,
					null, false));
			try {
				mockMvc.perform(patch(CATEGORIES + "/" + category.getId())
						.header(HttpHeaders.IF_MATCH, "\"rev-0\"").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Second\"}"))
						.andExpect(status().isPreconditionFailed());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			transaction.setRollbackOnly();
		});

		mockMvc.perform(get(CATEGORIES))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].name").value("First"))
				.andExpect(jsonPath("$.data[0].version").value(1));
	}

}
