package com.fmatrestaurant.menu.application;

import java.util.UUID;

/**
 * Thrown when a category does not exist.
 */
public class CategoryNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CategoryNotFoundException(UUID id) {
		super("Category not found with id " + id);
	}

}
