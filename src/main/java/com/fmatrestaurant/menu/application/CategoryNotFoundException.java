package com.fmatrestaurant.menu.application;

/**
 * Thrown when a category does not exist.
 */
public class CategoryNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CategoryNotFoundException(Long id) {
		super("Category not found with id " + id);
	}

}
