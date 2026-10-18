package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.ServletWebRequest;

class InventoryMockControllerTest {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InventoryMockController())
			.setControllerAdvice(new ApiExceptionHandler()).build();

	@Test
	void listsTenItemsWithAndWithoutTrailingSlash() throws Exception {
		for (String path : new String[] {"/inventory/items/", "/inventory/items", "/api/v1/inventory/items"}) {
			mvc.perform(get(path))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.data.length()").value(10))
					.andExpect(jsonPath("$.data[0].id").value("2c21767f-0f9a-4e9f-adc7-2730556c1801"))
					.andExpect(jsonPath("$.meta.page").value(1))
					.andExpect(jsonPath("$.meta.pageSize").value(12))
					.andExpect(jsonPath("$.meta.total").value(10))
					.andExpect(jsonPath("$.meta.totalPages").value(1));
		}
	}

	@Test
	void preservesTheExtraDetailRoute() throws Exception {
		var body = new InventoryMockController().list(null, 1, 12,
				new ServletWebRequest(new MockHttpServletRequest())).getBody();
		assertNotNull(body);
		for (var item : body.data()) {
			assertDoesNotThrow(() -> UUID.fromString(item.id()));
			mvc.perform(get("/inventory/items/{id}", item.id()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(item.id()))
					.andExpect(jsonPath("$.data.name").value(item.name()))
					.andExpect(jsonPath("$.data.unit").value(item.unit()));
		}
	}

	@Test
	void filtersAndPaginatesWithTotalsForTheFilteredCollection() throws Exception {
		mvc.perform(get("/api/v1/inventory/items").param("q", "CARNE").param("pageSize", "1").param("page", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Carne de cerdo"))
				.andExpect(jsonPath("$.meta.total").value(2))
				.andExpect(jsonPath("$.meta.totalPages").value(2));
		mvc.perform(get("/api/v1/inventory/items").param("q", "missing"))
				.andExpect(jsonPath("$.data.length()").value(0))
				.andExpect(jsonPath("$.meta.totalPages").value(0));
		mvc.perform(get("/api/v1/inventory/items").param("page", "2147483647"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void supportsConditionalGetAndRejectsNonPositivePagination() throws Exception {
		String etag = mvc.perform(get("/api/v1/inventory/items")).andReturn().getResponse().getHeader("ETag");
		assertNotNull(etag);
		mvc.perform(get("/api/v1/inventory/items").header("If-None-Match", etag))
				.andExpect(status().isNotModified()).andExpect(content().string(""));
		for (String param : new String[] {"page", "pageSize"}) {
			mvc.perform(get("/api/v1/inventory/items").param(param, "0"))
					.andExpect(status().isUnprocessableContent());
		}
	}

	@Test
	void unknownIdReturnsEmptyBodyOnTheExtraDetailRoute() throws Exception {
		for (String id : new String[] {"missing-item", "inv-beef-001"}) {
			mvc.perform(get("/inventory/items/{id}", id))
					.andExpect(status().isNoContent())
					.andExpect(content().string(""));
		}
	}

}
