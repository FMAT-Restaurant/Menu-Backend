package com.fmatrestaurant.menu.domain;

/**
 * Thrown when category data violates a domain rule.
 */
public class InvalidCategoryException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public InvalidCategoryException(String message) {
		super(message);
	}

}
