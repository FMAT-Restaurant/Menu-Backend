package com.fmatrestaurant.menu.api.security;

import static com.fmatrestaurant.menu.api.security.PatchPermissionRule.Change.STATUS;
import static com.fmatrestaurant.menu.api.security.PatchPermissionRule.Change.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fmatrestaurant.menu.api.security.PatchPermissionRule.Change;
import com.fmatrestaurant.menu.application.service.CatalogOfferService.CompositionShape;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PatchPermissionRuleTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final UUID SLOT = UUID.fromString("00000000-0000-4000-8000-000000000001");

	private static final UUID OPTION_A = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

	private static final UUID OPTION_B = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

	private static final Optional<List<CompositionShape>> CURRENT =
			Optional.of(List.of(new CompositionShape(SLOT, List.of(OPTION_A, OPTION_B))));

	@Test
	@DisplayName("Entrada: solo status es cambio de estado; otros campos, de gestión; ambos, los dos")
	void entryChanges() {
		assertEquals(Set.of(STATUS), entry("{\"status\":\"ACTIVE\"}"));
		assertEquals(Set.of(WRITE), entry("{\"brandName\":\"Otra\"}"));
		assertEquals(Set.of(WRITE, STATUS), entry("{\"status\":\"ACTIVE\",\"brandName\":\"Otra\"}"));
		assertEquals(Set.of(WRITE), entry("{\"status\":null,\"brandName\":\"Otra\"}"));
		assertEquals(Set.of(WRITE), entry("{}"));
		assertEquals(Set.of(WRITE), PatchPermissionRule.entryChanges(null));
	}

	@Test
	@DisplayName("Oferta: el status de las opciones con la misma composición es cambio de estado")
	void optionStatusIsAStatusChange() {
		assertEquals(Set.of(STATUS), offer(composition(option(OPTION_A, ",\"status\":\"INACTIVE\""),
				option(OPTION_B, ""))));
		assertEquals(Set.of(STATUS), offer("{\"status\":\"ACTIVE\"}"));
		assertEquals(Set.of(WRITE, STATUS), offer("{\"status\":\"ACTIVE\",\"basePrice\":190}"));
		assertEquals(Set.of(WRITE, STATUS), offer("{\"basePrice\":190,\"composition\":"
				+ composition(option(OPTION_A, ",\"status\":\"INACTIVE\""), option(OPTION_B, "")).substring(15)));
	}

	@Test
	@DisplayName("Oferta: quitar, reordenar o editar miembros de la composición es cambio de gestión")
	void structuralCompositionChangesAreManagementChanges() {
		// Dropping an option; the status sent for the other one is still a status change.
		assertEquals(Set.of(WRITE), offer(composition(option(OPTION_A, ""))));
		assertEquals(Set.of(WRITE, STATUS), offer(composition(option(OPTION_A, ",\"status\":\"INACTIVE\""))));
		// Reordering.
		assertEquals(Set.of(WRITE), offer(composition(option(OPTION_B, ""), option(OPTION_A, ""))));
		// Editing an option.
		assertEquals(Set.of(WRITE, STATUS), offer(composition(
				option(OPTION_A, ",\"status\":\"INACTIVE\",\"displayName\":\"Otra\""), option(OPTION_B, ""))));
		// A new option without id.
		assertEquals(Set.of(WRITE), offer(composition(option(OPTION_A, ""), option(OPTION_B, ""),
				"{\"sourceType\":\"RECIPE\",\"status\":\"ACTIVE\",\"recipeId\":\"" + UUID.randomUUID() + "\"}")));
		// Editing the slot.
		assertEquals(Set.of(WRITE), offer("{\"composition\":{\"slots\":[{\"slotId\":\"" + SLOT
				+ "\",\"name\":\"Otro\"}]}}"));
		// Unknown offer.
		assertEquals(Set.of(WRITE, STATUS), PatchPermissionRule.offerChanges(json(composition(option(OPTION_A, ""),
				option(OPTION_B, ",\"status\":\"INACTIVE\""))), Optional.empty()));
	}

	@Test
	@DisplayName("Oferta: un slot sin options conserva sus opciones y no cambia la estructura")
	void slotWithoutOptionsKeepsThem() {
		assertEquals(Set.of(WRITE), offer("{\"composition\":{\"slots\":[{\"slotId\":\"" + SLOT + "\"}]}}"));
		assertEquals(Set.of(WRITE, STATUS), offer("{\"status\":\"INACTIVE\",\"presentationTag\":\"Doble\","
				+ "\"composition\":{\"slots\":[{\"slotId\":\"" + SLOT + "\"}]}}"));
		assertEquals(Set.of(STATUS), offer("{\"status\":\"INACTIVE\","
				+ "\"composition\":{\"slots\":[{\"slotId\":\"" + SLOT + "\"}]}}"));
	}

	private static Set<Change> entry(String body) {
		return PatchPermissionRule.entryChanges(json(body));
	}

	private static Set<Change> offer(String body) {
		return PatchPermissionRule.offerChanges(json(body), CURRENT);
	}

	private static String composition(String... options) {
		return "{\"composition\":{\"slots\":[{\"slotId\":\"" + SLOT + "\",\"options\":[" + String.join(",", options)
				+ "]}]}}";
	}

	private static String option(UUID id, String fields) {
		return "{\"optionId\":\"" + id + "\",\"sourceType\":\"INVENTORY_ITEM\"" + fields + "}";
	}

	private static JsonNode json(String body) {
		return JSON.readTree(body);
	}

}
