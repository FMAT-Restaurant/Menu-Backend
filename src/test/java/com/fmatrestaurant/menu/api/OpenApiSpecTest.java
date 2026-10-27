package com.fmatrestaurant.menu.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fmatrestaurant.menu.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

	@Autowired
	private MockMvc mockMvc;

	/** The bundle of Menu-Documentation, self-contained, with the five operations of offers and idempotency. */
	@Test
	void servesTheCurrentContractBundle() throws Exception {
		mockMvc.perform(get("/openapi.yaml"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("version: 7.0.0")))
				.andExpect(content().string(Matchers.stringContainsInOrder("operationId: listMenuOffers",
						"operationId: createEntryOffer", "operationId: getMenuOffer", "operationId: patchMenuOffer",
						"operationId: deleteMenuOffer")))
				.andExpect(content().string(Matchers.containsString("InventoryItemSlotOptionInput:")))
				.andExpect(content().string(Matchers.containsString("CatalogOfferPatchResponse:")))
				.andExpect(content().string(Matchers.stringContainsInOrder("IdempotencyKey:", "name: Idempotency-Key",
						"IdempotencyBadRequest:", "INVALID_IDEMPOTENCY_KEY", "IdempotencyConflict:", "Retry-After:",
						"IDEMPOTENCY_KEY_REUSED", "IDEMPOTENCY_IN_PROGRESS")))
				// RECIPE options only carry recipeId; the resolved revision stays internal.
				.andExpect(content().string(Matchers.containsString("las opciones no exponen recipeRevision")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("recipeRevision:"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString(".yaml#"))));
	}

}
