package com.fmatrestaurant.menu.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Immutable, independent definition of the slots and options of a published offer revision. */
public record CompositionSnapshot(List<Slot> slots) {

	public CompositionSnapshot {
		slots = List.copyOf(slots);
		if (slots.isEmpty()) {
			throw new IllegalArgumentException("A composition snapshot must have at least one slot");
		}
	}

	public static CompositionSnapshot of(CatalogOffer offer) {
		return new CompositionSnapshot(offer.getSlots().stream().map(Slot::of).toList());
	}

	public record Slot(UUID id, String name, int quantity, String course, List<Option> options) {
		public Slot {
			options = List.copyOf(options);
			if (options.isEmpty()) {
				throw new IllegalArgumentException("A historical slot must have at least one option");
			}
		}

		static Slot of(CompositionSlot slot) {
			return new Slot(slot.getId(), slot.getName(), slot.getQuantity(), slot.getCourse(),
					slot.getOptions().stream().map(Option::of).toList());
		}

		public OfferStatus status() {
			return options.stream().anyMatch(option -> option.status() == OfferStatus.ACTIVE)
					? OfferStatus.ACTIVE : OfferStatus.INACTIVE;
		}
	}

	public record Option(UUID id, String displayName, OfferStatus status, SourceType sourceType,
			String inventoryItemId, String sourceName, BigDecimal quantity, String unit,
			UUID recipeId, Integer recipeRevision) {
		static Option of(SlotOption option) {
			return new Option(option.getId(), option.getDisplayName(), option.getStatus(), option.getSourceType(),
					option.getInventoryItemId(), option.getSourceName(), option.getQuantity(), option.getUnit(),
					option.getRecipeId(), option.getRecipeRevision());
		}
	}
}
