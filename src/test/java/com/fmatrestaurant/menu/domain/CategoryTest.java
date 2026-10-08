package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CategoryTest {

	@Test
	void keepsNameDescriptionAndMenu() {
		Category category = new Category(1L, "Bebidas", "Bebidas frías y calientes");

		assertEquals(1L, category.getMenuId());
		assertEquals("Bebidas", category.getName());
		assertEquals("Bebidas frías y calientes", category.getDescription());
	}

	@Test
	void trimsNameAndDescription() {
		Category category = new Category(1L, "  Postres ", " Dulces ");

		assertEquals("Postres", category.getName());
		assertEquals("Dulces", category.getDescription());
	}

	@Test
	void allowsMissingDescription() {
		Category category = new Category(1L, "Entradas", null);

		assertNull(category.getDescription());
	}

	@Test
	void rejectsBlankName() {
		assertThrows(IllegalArgumentException.class, () -> new Category(1L, "  ", "x"));
		assertThrows(IllegalArgumentException.class, () -> new Category(1L, null, "x"));
	}

	@Test
	void rejectsMissingMenu() {
		assertThrows(IllegalArgumentException.class, () -> new Category(null, "Bebidas", "x"));
	}

	@Test
	void updateChangesDetailsButKeepsMenu() {
		Category category = new Category(1L, "Bebidas", "Viejo");

		category.update("Bebidas frías", "Nuevo");

		assertEquals("Bebidas frías", category.getName());
		assertEquals("Nuevo", category.getDescription());
		assertEquals(1L, category.getMenuId());
	}

	@Test
	void updateRejectsBlankName() {
		Category category = new Category(1L, "Bebidas", "x");

		assertThrows(IllegalArgumentException.class, () -> category.update(" ", "x"));
		assertEquals("Bebidas", category.getName());
	}

}
