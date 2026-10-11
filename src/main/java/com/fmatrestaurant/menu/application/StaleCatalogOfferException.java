package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when an offer changed since the version the client read.
 */
public class StaleCatalogOfferException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public StaleCatalogOfferException(UUID id) {
		super("Catalog offer " + id + " changed since it was read");
	}

}
