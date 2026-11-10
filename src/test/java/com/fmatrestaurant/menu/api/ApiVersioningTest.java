package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.fmatrestaurant.menu.api.controller.CategoryController;
import com.fmatrestaurant.menu.application.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CategoryController.class)
@Import(ApiVersioningConfiguration.class)
class ApiVersioningTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CategoryService categories;

	@Test
	void routesAreServedUnderTheCurrentVersion() throws Exception {
		when(categories.list(1, 12)).thenReturn(new CategoryService.CategoryPage(new PageImpl<>(List.of()), Map.of()));

		mvc.perform(get("/api/v1/menu/categories")).andExpect(status().isOk());
	}

	@Test
	void aVersionNobodyServesIsRejectedWithTheErrorEnvelope() throws Exception {
		mvc.perform(get("/api/v2/menu/categories"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("Unsupported or missing API version; use /api/v1."));
	}

	@Test
	void aPathWithoutVersionIsNotServed() throws Exception {
		mvc.perform(get("/api/menu/categories")).andExpect(status().isNotFound());
	}

	@Test
	void theUnprefixedRouteIsNotServed() throws Exception {
		mvc.perform(get("/menu/categories")).andExpect(status().isNotFound());
	}

	@Test
	void pathsOutsideTheApiAreNotVersioned() throws Exception {
		mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
	}

	@Test
	void parserReadsTheVAsOptional() {
		var parser = new ApiVersioningConfiguration.PrefixedVersionParser();
		assertEquals(1, parser.parseVersion("v1"));
		assertEquals(1, parser.parseVersion("1"));
		assertEquals(12, parser.parseVersion("V12"));
		for (String bad : List.of("v", "", "x", "v1.2", "1a")) {
			assertThrows(IllegalArgumentException.class, () -> parser.parseVersion(bad));
		}
	}
}
