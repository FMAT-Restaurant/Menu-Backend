package com.fmatrestaurant.menu.domain;

/**
 * Thrown when category data violates a domain rule.
 */
public class InvalidCategoryException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;
	private final String path;

	public InvalidCategoryException(String message) {
		this("/", message);
	}

	public InvalidCategoryException(String path, String message) {
		super(message);
		this.path = path;
	}

	public String getPath() {
		return path;
	}

}
