package com.fmatrestaurant.menu.domain;

/**
 * Thrown when a field violates a domain rule. The path points to the offending field
 * of the request, for example {@code /brandName}.
 */
public class InvalidFieldException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	private final String path;

	public InvalidFieldException(String path, String message) {
		super(message);
		this.path = path;
	}

	public String getPath() {
		return path;
	}

}
