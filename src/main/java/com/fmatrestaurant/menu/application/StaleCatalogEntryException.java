package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when a catalog entry changed since the version the client read.
 */
public class StaleCatalogEntryException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public StaleCatalogEntryException(UUID id) {
		super("Catalog entry " + id + " changed since it was read");
	}

}
