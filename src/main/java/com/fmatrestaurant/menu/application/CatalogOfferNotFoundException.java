package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when an offer does not exist.
 */
public class CatalogOfferNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CatalogOfferNotFoundException(UUID id) {
		super("Catalog offer not found with id " + id);
	}

}
