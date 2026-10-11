package com.fmatrestaurant.menu.domain;

/**
 * Validation shared by the offer, its slots and its options.
 */
final class Fields {

	private Fields() {
	}

	/** Returns the stripped text, or fails if it is missing, blank or too long. */
	static String text(String path, String label, String value, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new InvalidFieldException(path, "The " + label + " is required");
		}
		String stripped = value.strip();
		if (stripped.length() > maxLength) {
			throw new InvalidFieldException(path, "The " + label + " must not exceed " + maxLength + " characters");
		}
		return stripped;
	}

}
