package com.fmatrestaurant.menu.application;

public class CategoryNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CategoryNotFoundException(Long id) {
		super("No existe la categoría con id " + id);
	}

}
