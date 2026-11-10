package com.fmatrestaurant.menu.infrastructure.dto;

import com.fmatrestaurant.menu.domain.event.InventoryItemDto;

import java.util.List;

/** Payload of {@code menu.source.created} and {@code menu.source.updated}. */
public record PlateDtoContract(
        String sourceId,
        String name, // null when the source is an Inventory article
        List<InventoryItemDto> items
) {
}
