package com.fmatrestaurant.menu.domain;

/**
 * Inventory article as the Inventory service returned it (BR-MENU-018).
 * The id is an opaque string owned by Inventory, preserved without UUID parsing or normalization.
 */
public record InventoryItem(String id, String name, String unit) {
}
