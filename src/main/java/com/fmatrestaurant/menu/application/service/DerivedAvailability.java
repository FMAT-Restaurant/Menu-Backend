package com.fmatrestaurant.menu.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.domain.model.CatalogOffer;
import com.fmatrestaurant.menu.domain.model.CompositionSlot;
import com.fmatrestaurant.menu.domain.model.OfferStatus;
import com.fmatrestaurant.menu.domain.model.SlotOption;
import com.fmatrestaurant.menu.domain.model.SourceType;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;

/**
 * The derived, read-only availability {@code isAvailable} of options, slots, offers and entries (RF-DISP-01 to
 * RF-DISP-04). It is computed when a response is built from what Inventory communicated (BE-10) and from the
 * composition and the statuses, so it is never stale and nothing has to be propagated. It never changes a status
 * (RF-DISP-06.CA5).
 */
@Service
@Transactional(readOnly = true)
public class DerivedAvailability {

	private final RecipeRevisionRepository revisions;

	private final CatalogOfferRepository offers;

	public DerivedAvailability(RecipeRevisionRepository revisions, CatalogOfferRepository offers) {
		this.revisions = revisions;
		this.offers = offers;
	}

	/**
	 * The availability of an offer, its slots and its options.
	 *
	 * @param available whether every slot is available (RF-DISP-03.CA1)
	 * @param slots by slot id: whether at least one ACTIVE option is available (RF-DISP-02.CA1)
	 * @param options by option id: whether its source is available, whatever its status (RF-DISP-01)
	 */
	public record OfferAvailability(boolean available, Map<UUID, Boolean> slots, Map<UUID, Boolean> options) {

		public boolean slot(UUID slotId) {
			return slots.getOrDefault(slotId, false);
		}

		public boolean option(UUID optionId) {
			return options.getOrDefault(optionId, false);
		}

		/**
		 * Changes whenever one of the values of the representation changes, so it can be part of the ETag. The
		 * maps keep the order of the composition.
		 */
		public String fingerprint() {
			StringBuilder bits = new StringBuilder(available ? "1" : "0");
			slots.forEach((id, value) -> bits.append(id).append(value ? '1' : '0'));
			options.forEach((id, value) -> bits.append(id).append(value ? '1' : '0'));
			CRC32 crc = new CRC32();
			crc.update(bits.toString().getBytes(StandardCharsets.US_ASCII));
			return Long.toHexString(crc.getValue());
		}

	}

	public OfferAvailability offer(CatalogOffer offer) {
		return offers(List.of(offer)).get(offer.getId());
	}

	/** The availability of each offer, by offer id, reading the recipes of all of them at once. */
	public Map<UUID, OfferAvailability> offers(Collection<CatalogOffer> catalogOffers) {
		Map<UUID, Boolean> recipes = recipeAvailability(catalogOffers);
		Map<UUID, OfferAvailability> result = new HashMap<>();
		for (CatalogOffer offer : catalogOffers) {
			result.put(offer.getId(), compute(offer, recipes));
		}
		return result;
	}

	/** Whether at least one ACTIVE offer of the entry is available (RF-DISP-04.CA1). */
	public boolean entry(UUID entryId) {
		return entries(List.of(entryId)).getOrDefault(entryId, false);
	}

	/** The availability of each entry, by entry id; an entry without ACTIVE offers is not available. */
	public Map<UUID, Boolean> entries(Collection<UUID> entryIds) {
		Map<UUID, Boolean> result = new HashMap<>();
		entryIds.forEach(id -> result.put(id, false));
		if (entryIds.isEmpty()) {
			return result;
		}
		List<CatalogOffer> active = offers.findByEntryIdIn(entryIds).stream()
				.filter(offer -> offer.getStatus() == OfferStatus.ACTIVE).toList();
		Map<UUID, OfferAvailability> availability = offers(active);
		for (CatalogOffer offer : active) {
			if (availability.get(offer.getId()).available()) {
				result.put(offer.getEntry().getId(), true);
			}
		}
		return result;
	}

	private static OfferAvailability compute(CatalogOffer offer, Map<UUID, Boolean> recipes) {
		Map<UUID, Boolean> slots = new LinkedHashMap<>();
		Map<UUID, Boolean> options = new LinkedHashMap<>();
		boolean offerAvailable = true;
		for (CompositionSlot slot : offer.getSlots()) {
			boolean slotAvailable = false;
			for (SlotOption option : slot.getOptions()) {
				boolean optionAvailable = option.getSourceType() == SourceType.INVENTORY_ITEM
						? option.isSourceAvailable()
						: recipes.getOrDefault(option.getRecipeId(), false);
				options.put(option.getId(), optionAvailable);
				slotAvailable |= optionAvailable && option.getStatus() == OfferStatus.ACTIVE;
			}
			slots.put(slot.getId(), slotAvailable);
			offerAvailable &= slotAvailable;
		}
		return new OfferAvailability(offerAvailable, slots, options);
	}

	private Map<UUID, Boolean> recipeAvailability(Collection<CatalogOffer> catalogOffers) {
		Set<UUID> recipeIds = new HashSet<>();
		catalogOffers.forEach(offer -> offer.getSlots().forEach(slot -> slot.getOptions().stream()
				.filter(option -> option.getSourceType() == SourceType.RECIPE)
				.forEach(option -> recipeIds.add(option.getRecipeId()))));
		Map<UUID, Boolean> result = new HashMap<>();
		if (!recipeIds.isEmpty()) {
			for (Object[] row : revisions.findAvailabilityByRecipeIds(recipeIds)) {
				result.put((UUID) row[0], (Boolean) row[1]);
			}
		}
		return result;
	}

}
