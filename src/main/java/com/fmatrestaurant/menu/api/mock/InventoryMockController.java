package com.fmatrestaurant.menu.api.mock;

import java.util.List;
import java.util.Locale;

import com.fmatrestaurant.menu.application.exception.InventoryItemNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fmatrestaurant.menu.domain.model.InventoryItem;

/**
 * Fixed inventory responses for local clients, without persistence or external calls. It is registered
 * only in the {@code local} profile: anywhere else Menu exposes neither the list nor the detail of
 * articles, and its client queries the Inventory service configured in {@code menu.inventory.base-url}.
 */
@Profile("local")
@RestController
public class InventoryMockController {

	private static final List<InventoryItem> ITEMS = List.of(
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1801", "Carne de res", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1802", "Pollo", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1803", "Carne de cerdo", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1804", "Arroz", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1805", "Frijol", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1806", "Tomate", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1807", "Cebolla", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1808", "Queso", "kg"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1809", "Leche", "L"),
			new InventoryItem("2c21767f-0f9a-4e9f-adc7-2730556c1810", "Huevo", "unit"));

	/**
	 * Compatible with {@code GET /items} of Inventory: the plain list of articles, without pagination or
	 * envelope. {@code q} keeps only the articles whose name contains it, ignoring case.
	 */
	@GetMapping({"/api/v1/items", "/items"})
	public List<InventoryItem> list(@RequestParam(required = false) String q) {
		String query = q == null ? "" : q.toLowerCase(Locale.ROOT);
		return ITEMS.stream().filter(item -> item.name().toLowerCase(Locale.ROOT).contains(query)).toList();
	}

	/**
	 * Compatible with {@code GET /items/{id}} of Inventory, the route Menu's client calls: the article as a flat
	 * {@code {id, name, unit}} object, or {@code 404} with the error envelope if the id does not exist. Ids are
	 * opaque, so the match is exact.
	 */
	@GetMapping({"/api/v1/items/{id}", "/items/{id}"})
	public InventoryItem get(@PathVariable String id) {
		return ITEMS.stream()
				.filter(item -> item.id().equals(id))
				.findFirst()
				.orElseThrow(() -> new InventoryItemNotFoundException(id));
	}
}
