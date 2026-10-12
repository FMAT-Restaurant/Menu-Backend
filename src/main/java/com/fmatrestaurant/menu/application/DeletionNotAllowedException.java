package com.fmatrestaurant.menu.application;

/**
 * Thrown when the current state of a resource does not allow deleting it.
 */
public class DeletionNotAllowedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DeletionNotAllowedException(String message) {
		super(message);
	}

}
