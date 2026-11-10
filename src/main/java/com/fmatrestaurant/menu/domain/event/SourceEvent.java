package com.fmatrestaurant.menu.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Something happened to a source of the menu: a recipe of the library or an Inventory article offered in an
 * option. Menu announces it to the rest of the system in {@code menu.topic} with the routing key of its type.
 *
 * @param sourceId the recipe id, or the option id for an Inventory article
 * @param name name of the recipe; {@code null} for an Inventory article, and for a deletion
 * @param items the Inventory articles (with quantity) the source consumes; empty for a deletion
 */
public record SourceEvent(
		UUID eventId,
		Type type,
		String sourceId,
		String name,
		List<InventoryItemDto> items,
		Instant occurredOn
) {

	public enum Type {
		CREATED("menu.source.created"),
		UPDATED("menu.source.updated"),
		DELETED("menu.source.deleted");

		private final String routingKey;

		Type(String routingKey) {
			this.routingKey = routingKey;
		}

		public String routingKey() {
			return routingKey;
		}
	}

	public static SourceEvent created(UUID sourceId, String name, List<InventoryItemDto> items) {
		return new SourceEvent(UUID.randomUUID(), Type.CREATED, sourceId.toString(), name, items, Instant.now());
	}

	public static SourceEvent updated(UUID sourceId, String name, List<InventoryItemDto> items) {
		return new SourceEvent(UUID.randomUUID(), Type.UPDATED, sourceId.toString(), name, items, Instant.now());
	}

	public static SourceEvent deleted(UUID sourceId) {
		return new SourceEvent(UUID.randomUUID(), Type.DELETED, sourceId.toString(), null, List.of(), Instant.now());
	}
}
