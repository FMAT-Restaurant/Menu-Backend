package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RecipeTest {

	@Test
	void startsAtRevisionOne() {
		assertEquals(1, new Recipe().getCurrentRevision());
	}

	@Test
	void publishingMovesToTheNextRevision() {
		Recipe recipe = new Recipe();

		assertEquals(2, recipe.publishNextRevision());
		assertEquals(3, recipe.publishNextRevision());
		assertEquals(3, recipe.getCurrentRevision());
	}

}
