package com.fmatrestaurant.menu.domain;

import java.util.UUID;

/**
 * Inventory item data received by Menu and retained as a source snapshot (BR-MENU-018).
 */
public record InventoryItem(UUID id, String name, String unit) {
}
