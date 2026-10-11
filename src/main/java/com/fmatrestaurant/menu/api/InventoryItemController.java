package com.fmatrestaurant.menu.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fmatrestaurant.menu.application.InventoryItemService;
import com.fmatrestaurant.menu.domain.InventoryItem;

/**
 * Search of Inventory articles to select the content of slot options. The articles are read from
 * Inventory; Menu does not administer them nor their stock.
 */
@RestController
@RequestMapping("/api/v1/inventory/items")
public class InventoryItemController {

	private final InventoryItemService inventoryItemService;

	public InventoryItemController(InventoryItemService inventoryItemService) {
		this.inventoryItemService = inventoryItemService;
	}

	@GetMapping
	public InventoryItemListResponse search(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		Page<InventoryItem> items = inventoryItemService.search(q, page, pageSize);
		return new InventoryItemListResponse(items.getContent(),
				new PageMeta(page, pageSize, items.getTotalElements(), items.getTotalPages()));
	}

	public record InventoryItemListResponse(List<InventoryItem> data, PageMeta meta) {
	}

}
