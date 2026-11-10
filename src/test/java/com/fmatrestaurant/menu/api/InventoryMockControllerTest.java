package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fmatrestaurant.menu.api.mock.InventoryMockController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryMockControllerTest {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InventoryMockController())
			.setControllerAdvice(new ApiExceptionHandler()).build();

	@Test
	void listsTheTenItemsAsAPlainArray() throws Exception {
		for (String path : new String[] {"/items", "/api/v1/items"}) {
			mvc.perform(get(path))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.length()").value(10))
					.andExpect(jsonPath("$[0].id").value("2c21767f-0f9a-4e9f-adc7-2730556c1801"))
					.andExpect(jsonPath("$[0].name").value("Carne de res"))
					.andExpect(jsonPath("$[0].unit").value("kg"));
		}
	}

	@Test
	void answersTheDetailRouteOfInventory() throws Exception {
		var items = new InventoryMockController().list(null);
		for (var item : items) {
			assertDoesNotThrow(() -> UUID.fromString(item.id()));
			mvc.perform(get("/api/v1/items/{id}", item.id()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(item.id()))
					.andExpect(jsonPath("$.name").value(item.name()))
					.andExpect(jsonPath("$.unit").value(item.unit()));
		}
	}

	@Test
	void filtersByNameIgnoringCaseAndIgnoresAnyPaginationParameter() throws Exception {
		mvc.perform(get("/api/v1/items").param("q", "CARNE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("Carne de res"))
				.andExpect(jsonPath("$[1].name").value("Carne de cerdo"));
		mvc.perform(get("/api/v1/items").param("q", "missing"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/v1/items").param("page", "2").param("pageSize", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10));
	}

	/** Like Inventory: 404 with the error envelope, which is what the Menu client takes as "does not exist". */
	@Test
	void unknownIdIsNotFoundWithTheErrorEnvelopeOfInventory() throws Exception {
		for (String path : new String[] {"/items/{id}", "/api/v1/items/{id}"}) {
			for (String id : new String[] {"missing-item", "inv-beef-001", "2C21767F-0F9A-4E9F-ADC7-2730556C1801"}) {
				mvc.perform(get(path, id))
						.andExpect(status().isNotFound())
						.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
						.andExpect(jsonPath("$.error.message").isString())
						.andExpect(jsonPath("$.data").doesNotExist());
			}
		}
	}

	@Test
	void isRegisteredOnlyInTheLocalProfile() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(InventoryMockController.class);

		runner.run(context -> assertEquals(0, context.getBeansOfType(InventoryMockController.class).size()));
		runner.withPropertyValues("spring.profiles.active=production")
				.run(context -> assertEquals(0, context.getBeansOfType(InventoryMockController.class).size()));
		runner.withPropertyValues("spring.profiles.active=local")
				.run(context -> assertEquals(1, context.getBeansOfType(InventoryMockController.class).size()));
	}

}
