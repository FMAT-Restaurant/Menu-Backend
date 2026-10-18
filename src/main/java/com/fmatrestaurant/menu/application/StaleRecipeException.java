package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when a recipe changed since the version the client read.
 */
public class StaleRecipeException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public StaleRecipeException(UUID id) {
		super("Recipe " + id + " changed since it was read");
	}

}
