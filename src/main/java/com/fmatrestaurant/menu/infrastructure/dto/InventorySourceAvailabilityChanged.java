package com.fmatrestaurant.menu.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Payload of the availability event that Inventory publishes in {@code inventory.topic}: the source (recipe or
 * option) is identified by {@code sourceId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventorySourceAvailabilityChanged(
        String eventId,
        UUID sourceId,
        boolean isAvailable
) {
}
