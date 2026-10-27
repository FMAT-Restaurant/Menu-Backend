package com.fmatrestaurant.menu.api;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.InvalidFieldException;

/**
 * Fixed inventory responses for local clients, without persistence or external calls. It is registered
 * only in the {@code local} profile: anywhere else Menu exposes neither the list nor the detail of
 * articles, and its client queries the Inventory service configured in {@code menu.inventory.base-url}.
 */
@Profile("local")
@RestController
@RequestMapping({"/api/v1/inventory/items", "/inventory/items"})
public class InventoryMockController {

	/** The answer of Inventory for an article that does not exist, as its contract defines it. */
	private static final String NOT_FOUND = "No se encontró el recurso solicitado.";

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

	@GetMapping({"", "/"})
	public ResponseEntity<InventoryPage> list(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int pageSize,
			WebRequest request) {
		if (page < 1) {
			throw new InvalidFieldException("page", "page must be positive");
		}
		if (pageSize < 1) {
			throw new InvalidFieldException("pageSize", "pageSize must be positive");
		}
		String query = q == null ? "" : q.toLowerCase(Locale.ROOT);
		List<InventoryItem> matches = ITEMS.stream()
				.filter(item -> item.name().toLowerCase(Locale.ROOT).contains(query)).toList();
		int from = (int) Math.min((long) (page - 1) * pageSize, matches.size());
		int to = (int) Math.min((long) from + pageSize, matches.size());
		InventoryPage body = new InventoryPage(matches.subList(from, to),
				new PageMeta(page, pageSize, matches.size(), (int) ((matches.size() + (long) pageSize - 1) / pageSize)));
		String etag = "\"inventory-mock-" + page + "-" + pageSize + "-"
				+ matches.stream().map(InventoryItem::id).toList().hashCode() + "\"";
		if (request.checkNotModified(etag)) {
			return ResponseEntity.status(304).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).body(body);
	}

	public record InventoryPage(List<InventoryItem> data, PageMeta meta) {
	}

	/**
	 * Compatible with {@code GET /inventory/items/{id}} of Inventory: {@code {data: {id, name, unit}}}, or
	 * {@code 404} with the error envelope if the id does not exist. Ids are opaque, so the match is exact.
	 */
	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable String id) {
		return ITEMS.stream()
				.filter(item -> item.id().equals(id))
				.findFirst()
				.<ResponseEntity<?>>map(item -> ResponseEntity.ok(new DataResponse<>(item)))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new ApiExceptionHandler.ErrorEnvelope(new ApiExceptionHandler.ErrorBody(NOT_FOUND, null))));
	}
}
