package com.fmatrestaurant.menu.domain.event;

import java.math.BigDecimal;

public record InventoryItemDto(String id, BigDecimal quantity) {
}
