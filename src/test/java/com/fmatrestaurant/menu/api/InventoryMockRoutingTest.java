package com.fmatrestaurant.menu.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fmatrestaurant.menu.api.controller.CategoryController;
import com.fmatrestaurant.menu.api.mock.InventoryMockController;
import com.fmatrestaurant.menu.application.service.CategoryService;

/**
 * The mock with the real MVC configuration, API versioning included: it is an independent service, so its
 * routes outside {@code /api} carry no version and must not be rejected for lacking one.
 */
// A Menu controller declares v1, as in the full application; alone, the mock would not make v1 a known version.
@WebMvcTest(controllers = { InventoryMockController.class, CategoryController.class })
@Import({ ApiVersioningConfiguration.class, ApiExceptionHandler.class })
@ActiveProfiles("local")
class InventoryMockRoutingTest {

	private static final String ID = "2c21767f-0f9a-4e9f-adc7-2730556c1801";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CategoryService categories;

	@Test
	void theDetailRouteIsServedWithAndWithoutTheVersionPrefix() throws Exception {
		for (String path : new String[] {"/items/{id}", "/api/v1/items/{id}"}) {
			mvc.perform(get(path, ID)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ID));
			mvc.perform(get(path, "missing")).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.message").isString());
		}
	}

	@Test
	void theListRouteIsServedWithoutTheVersionPrefix() throws Exception {
		mvc.perform(get("/items")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
	}
}
