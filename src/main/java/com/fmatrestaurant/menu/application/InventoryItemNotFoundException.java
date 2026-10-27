package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when an inventory item does not exist.
 */
public class InventoryItemNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InventoryItemNotFoundException(String id) {
        super("Inventory item not found with id " + id);
    }
}
