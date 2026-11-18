package com.fmatrestaurant.menu.api.security;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fmatrestaurant.menu.application.service.CatalogOfferService.CompositionShape;

import tools.jackson.databind.JsonNode;

/**
 * Tells which kinds of change a PATCH of an entry or an offer makes (RF-GEN-01.CA3): a body without status
 * changes requires the management permission, one with only status changes requires the status permission,
 * and one that mixes both requires both. Changing the status of an option of an offer is a status change.
 */
public final class PatchPermissionRule {

	/** A kind of change, each protected by its own permission. */
	public enum Change {
		/** Any change other than a status: requires the management ({@code write}) permission. */
		WRITE,
		/** A change of an administrative status: requires the {@code status} permission. */
		STATUS
	}

	private static final String STATUS = "status";

	private static final Set<String> SLOT_IDENTITY = Set.of("slotId", "options");

	private static final Set<String> OPTION_IDENTITY = Set.of("optionId", "sourceType", STATUS);

	private PatchPermissionRule() {
	}

	/** The changes of a PATCH of an entry; a body that is not a JSON object counts as a management change. */
	public static Set<Change> entryChanges(JsonNode body) {
		if (body == null || !body.isObject()) {
			return EnumSet.of(Change.WRITE);
		}
		Set<Change> changes = EnumSet.noneOf(Change.class);
		body.properties().forEach(field -> {
			if (!field.getValue().isNull()) {
				changes.add(STATUS.equals(field.getKey()) ? Change.STATUS : Change.WRITE);
			}
		});
		return orWrite(changes);
	}

	/**
	 * The changes of a PATCH of an offer. The composition counts only as status changes when it lists exactly
	 * the current slots and options, in the same order, and carries nothing but their ids, source types and
	 * statuses; otherwise it adds, removes, reorders or edits members, which is a management change.
	 *
	 * @param current the current composition, or empty if the offer does not exist
	 */
	public static Set<Change> offerChanges(JsonNode body, Optional<List<CompositionShape>> current) {
		if (body == null || !body.isObject()) {
			return EnumSet.of(Change.WRITE);
		}
		Set<Change> changes = EnumSet.noneOf(Change.class);
		body.properties().forEach(field -> {
			JsonNode value = field.getValue();
			if (value.isNull()) {
				return;
			}
			switch (field.getKey()) {
				case STATUS -> changes.add(Change.STATUS);
				case "composition" -> changes.addAll(compositionChanges(value, current));
				default -> changes.add(Change.WRITE);
			}
		});
		return orWrite(changes);
	}

	/**
	 * Every status sent for an existing option is a status change, whatever else the body changes; a new
	 * option (without id) is created with its status, which is part of the management change.
	 */
	private static Set<Change> compositionChanges(JsonNode composition, Optional<List<CompositionShape>> current) {
		Set<Change> changes = EnumSet.noneOf(Change.class);
		JsonNode slots = composition.get("slots");
		if (!composition.isObject() || composition.size() != 1 || slots == null || !slots.isArray()) {
			return EnumSet.of(Change.WRITE);
		}
		List<CompositionShape> shape = current.orElse(List.of());
		if (current.isEmpty() || slots.size() != shape.size()) {
			changes.add(Change.WRITE);
		}
		for (int index = 0; index < slots.size(); index++) {
			JsonNode slot = slots.get(index);
			if (!slot.isObject()) {
				changes.add(Change.WRITE);
				continue;
			}
			CompositionShape existing = index < shape.size() ? shape.get(index) : null;
			if (!onlyFields(slot, SLOT_IDENTITY) || existing == null
					|| !existing.slotId().equals(uuid(slot.get("slotId")))) {
				changes.add(Change.WRITE);
			}
			JsonNode options = slot.get("options");
			if (options == null) {
				continue;
			}
			if (!options.isArray() || existing == null || !existing.optionIds().equals(optionIds(options))) {
				changes.add(Change.WRITE);
			}
			if (options.isArray()) {
				options.forEach(option -> changes.addAll(optionChanges(option)));
			}
		}
		return changes;
	}

	private static Set<Change> optionChanges(JsonNode option) {
		if (!option.isObject() || uuid(option.get("optionId")) == null) {
			return EnumSet.of(Change.WRITE);
		}
		Set<Change> changes = EnumSet.noneOf(Change.class);
		if (!onlyFields(option, OPTION_IDENTITY)) {
			changes.add(Change.WRITE);
		}
		JsonNode status = option.get(STATUS);
		if (status != null && !status.isNull()) {
			changes.add(Change.STATUS);
		}
		return changes;
	}

	private static boolean onlyFields(JsonNode node, Set<String> allowed) {
		return node.propertyNames().stream().allMatch(allowed::contains);
	}

	private static List<UUID> optionIds(JsonNode options) {
		List<UUID> ids = new ArrayList<>();
		for (JsonNode option : options) {
			ids.add(option.isObject() ? uuid(option.get("optionId")) : null);
		}
		return ids;
	}

	/** The UUID of a JSON string, or {@code null} if it is missing or malformed. */
	private static UUID uuid(JsonNode value) {
		if (value == null || !value.isString()) {
			return null;
		}
		try {
			return UUID.fromString(value.stringValue());
		} catch (IllegalArgumentException _) {
			return null;
		}
	}

	/** An empty body changes nothing, but it is still an edit of the resource. */
	private static Set<Change> orWrite(Set<Change> changes) {
		return changes.isEmpty() ? EnumSet.of(Change.WRITE) : changes;
	}

}
