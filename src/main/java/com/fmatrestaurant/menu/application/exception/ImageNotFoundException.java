package com.fmatrestaurant.menu.application.exception;

import java.util.UUID;

/**
 * Thrown when an image does not exist.
 */
public class ImageNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ImageNotFoundException(UUID id) {
		super("Image not found with id " + id);
	}

}
