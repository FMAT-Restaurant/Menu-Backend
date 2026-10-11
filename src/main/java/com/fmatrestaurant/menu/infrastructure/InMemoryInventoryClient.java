package com.fmatrestaurant.menu.infrastructure;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fmatrestaurant.menu.domain.InventoryItem;

/**
 * Fixed Inventory articles for local testing, active only with the {@code inventory-stub} profile.
 *
 * <p>ponytail: stands in for Inventory while it has no contract; delete it when the real service is
 * connected.
 */
@Component
@Primary
@Profile("inventory-stub")
public class InMemoryInventoryClient extends InventoryClient {

	static final List<InventoryItem> ITEMS = List.of(
			new InventoryItem(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Papas", "g"),
			new InventoryItem(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Carne molida", "g"),
			new InventoryItem(UUID.fromString("00000000-0000-0000-0000-000000000003"), "Queso cheddar", "g"),
			new InventoryItem(UUID.fromString("00000000-0000-0000-0000-000000000004"), "Jugo de naranja", "ml"),
			new InventoryItem(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Refresco de cola", "ml"),
			new InventoryItem(UUID.fromString("00000000-0000-0000-0000-000000000006"), "Pan para hamburguesa", "pieza"));

	public InMemoryInventoryClient() {
		super("http://unused");
	}

	@Override
	public Optional<InventoryItem> find(UUID id) {
		return ITEMS.stream().filter(item -> item.id().equals(id)).findFirst();
	}

	/** Case-insensitive search by name, paginated like the contract. */
	@Override
	public InventoryItemPage search(String q, int page, int pageSize) {
		String text = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		List<InventoryItem> found = ITEMS.stream()
				.filter(item -> item.name().toLowerCase(Locale.ROOT).contains(text)).toList();
		List<InventoryItem> pageItems = found.stream().skip((long) (page - 1) * pageSize).limit(pageSize).toList();
		return new InventoryItemPage(pageItems,
				new Meta(page, pageSize, found.size(), (found.size() + pageSize - 1) / pageSize));
	}

}
