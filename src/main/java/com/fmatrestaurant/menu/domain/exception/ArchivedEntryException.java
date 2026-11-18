package com.fmatrestaurant.menu.domain.exception;

/**
 * Thrown when a change reaches an ARCHIVED entry or one of its offers: the only change allowed is
 * unarchiving the entry to INACTIVE (RF-ENT-02.CA4).
 */
public class ArchivedEntryException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ArchivedEntryException(String message) {
		super(message);
	}

}
