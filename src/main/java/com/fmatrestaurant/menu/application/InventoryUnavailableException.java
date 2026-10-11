package com.fmatrestaurant.menu.application;

/**
 * Thrown when the Inventory service cannot answer.
 */
public class InventoryUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InventoryUnavailableException(Throwable cause) {
		super("The Inventory service is not available", cause);
	}

}
