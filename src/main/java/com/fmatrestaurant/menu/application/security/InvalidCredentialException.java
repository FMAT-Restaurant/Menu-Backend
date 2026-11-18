package com.fmatrestaurant.menu.application.security;

/** The credential cannot be read: it is answered with 401. */
public class InvalidCredentialException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidCredentialException(String message) {
		super(message);
	}

}
