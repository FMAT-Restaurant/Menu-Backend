package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when a recipe, or a revision of it, does not exist.
 */
public class RecipeNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecipeNotFoundException(UUID id) {
		super("Recipe not found with id " + id);
	}

	public RecipeNotFoundException(UUID id, int revision) {
		super("Recipe " + id + " has no revision " + revision);
	}

}
