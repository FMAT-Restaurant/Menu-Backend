package com.fmatrestaurant.menu.application.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;
import com.fmatrestaurant.menu.infrastructure.repository.SlotOptionRepository;

/**
 * Keeps the availability that Inventory communicates for each content source (RF-INTG-03, RF-DISP-01): a recipe,
 * whose availability holds for every revision because the source is the recipe, or an {@code INVENTORY_ITEM}
 * option. No event changes an administrative status (RF-DISP-06.CA5).
 *
 * <p>The updates are bulk statements: they do not touch the versions behind the ETags, so an event never makes
 * the If-Match of an administrator stale, and the entities never write these columns back.
 */
@Service
public class AvailabilityService {

	/** What an event did. */
	public enum Outcome {
		/** The availability of every revision of the recipe was set. */
		RECIPE,
		/** The availability of the {@code INVENTORY_ITEM} option was set. */
		OPTION,
		/** No recipe nor {@code INVENTORY_ITEM} option has that id, for instance because it was deleted. */
		UNKNOWN_SOURCE,
		/** The event was already processed; it has no effect. */
		DUPLICATE
	}

	private final ProcessedEventRepository processedEvents;

	private final RecipeRevisionRepository revisions;

	private final SlotOptionRepository options;

	public AvailabilityService(ProcessedEventRepository processedEvents, RecipeRevisionRepository revisions,
			SlotOptionRepository options) {
		this.processedEvents = processedEvents;
		this.revisions = revisions;
		this.options = options;
	}

	/**
	 * Sets the availability of the source. The event id is recorded in the same transaction, so a redelivered or
	 * repeated event has no effect, also across instances. Without an id the event is applied anyway: setting a
	 * value twice gives the same result.
	 */
	@Transactional
	public Outcome apply(String eventId, UUID sourceId, boolean available) {
		if (eventId != null && !eventId.isBlank() && processedEvents.markProcessed(eventId, Instant.now()) == 0) {
			return Outcome.DUPLICATE;
		}
		if (revisions.updateAvailability(sourceId, available) > 0) {
			return Outcome.RECIPE;
		}
		if (options.updateSourceAvailability(sourceId, available) > 0) {
			return Outcome.OPTION;
		}
		return Outcome.UNKNOWN_SOURCE;
	}

}
