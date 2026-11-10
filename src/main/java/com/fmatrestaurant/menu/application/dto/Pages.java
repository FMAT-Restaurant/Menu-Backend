package com.fmatrestaurant.menu.application.dto;

import com.fmatrestaurant.menu.domain.exception.InvalidFieldException;

/**
 * Validation of the pagination parameters of the lists.
 */
public final class Pages {

	private Pages() {
	}

	/**
	 * @param page page number, starting at 1
	 * @throws InvalidFieldException if the page or the page size are not valid
	 */
	public static void check(int page, int pageSize) {
		if (page < 1) {
			throw new InvalidFieldException("page", "The page must be 1 or greater");
		}
		if (pageSize < 1) {
			throw new InvalidFieldException("pageSize", "The page size must be 1 or greater");
		}
	}

}
