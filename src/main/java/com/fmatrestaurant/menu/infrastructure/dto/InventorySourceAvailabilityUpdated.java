package com.fmatrestaurant.menu.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Payload of {@code inventory.source.availability.updated} (AsyncAPI 1.0.0): the availability of a content source,
 * a recipe or an {@code INVENTORY_ITEM} option, identified by {@code sourceId}. The contract only defines
 * {@code sourceId} and {@code isAvailable}; the event id travels as the AMQP {@code messageId}, and
 * {@code eventId} is read as a fallback when a publisher sends it in the body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventorySourceAvailabilityUpdated(
        String eventId,
        UUID sourceId,
        Boolean isAvailable
) {
}
