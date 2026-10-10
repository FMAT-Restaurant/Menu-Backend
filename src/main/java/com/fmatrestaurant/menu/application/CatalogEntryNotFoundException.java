package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when a catalog entry does not exist.
 */
public class CatalogEntryNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CatalogEntryNotFoundException(UUID id) {
		super("Catalog entry not found with id " + id);
	}

}
