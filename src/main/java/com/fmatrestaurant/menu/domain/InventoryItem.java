package com.fmatrestaurant.menu.domain;

import java.util.UUID;

/**
 * Inventory article as the Inventory service returned it (BR-MENU-018).
 */
public record InventoryItem(UUID id, String name, String unit) {
}
