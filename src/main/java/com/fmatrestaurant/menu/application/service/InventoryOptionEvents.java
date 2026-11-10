package com.fmatrestaurant.menu.application.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fmatrestaurant.menu.application.EventPublisher;
import com.fmatrestaurant.menu.domain.event.InventoryItemDto;
import com.fmatrestaurant.menu.domain.event.SourceEvent;
import com.fmatrestaurant.menu.domain.model.CompositionSlot;
import com.fmatrestaurant.menu.domain.model.SourceType;

/**
 * Announces the Inventory articles offered in options as sources: each option with Inventory content is a source
 * identified by its option id. Options are compared before and after a change, so only what really changed is
 * announced: a new option is created, one whose article or quantity changed is updated, one that disappeared is
 * deleted.
 */
public final class InventoryOptionEvents {

	/** What an option consumes from Inventory, copied because the options are modified in place. */
	public record Stock(String inventoryItemId, BigDecimal quantity) {

		boolean sameAs(Stock other) {
			return Objects.equals(inventoryItemId, other.inventoryItemId)
					&& quantity.compareTo(other.quantity) == 0;
		}

		public List<InventoryItemDto> items() {
			return List.of(new InventoryItemDto(inventoryItemId, quantity));
		}
	}

	private InventoryOptionEvents() {
	}

	/** The Inventory options of the slots, by option id. */
	public static Map<UUID, Stock> snapshot(Collection<CompositionSlot> slots) {
		Map<UUID, Stock> stock = new HashMap<>();
		for (CompositionSlot slot : slots) {
			slot.getOptions().stream()
					.filter(option -> option.getSourceType() == SourceType.INVENTORY_ITEM)
					.forEach(option -> stock.put(option.getId(),
							new Stock(option.getInventoryItemId(), option.getQuantity())));
		}
		return stock;
	}

	/** Announces the differences between two snapshots. */
	public static void publishChanges(EventPublisher publisher, Map<UUID, Stock> before, Map<UUID, Stock> after) {
		after.forEach((id, stock) -> {
			Stock previous = before.get(id);
			if (previous == null) {
				publisher.publish(SourceEvent.created(id, null, stock.items()));
			}
			else if (!previous.sameAs(stock)) {
				publisher.publish(SourceEvent.updated(id, null, stock.items()));
			}
		});
		before.keySet().stream()
				.filter(id -> !after.containsKey(id))
				.forEach(id -> publisher.publish(SourceEvent.deleted(id)));
	}
}
