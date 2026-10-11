package com.fmatrestaurant.menu.domain;

import java.util.UUID;

/**
 * Article owned by Inventory (BR-MENU-018). Menu only references it and never redefines it.
 */
public record InventoryItem(UUID id, String name, String unit) {
}
