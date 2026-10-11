package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CategoryTest {

	private static final Long MENU_ID = 1L;

	@Test
	void keepsNameDescriptionAndMenu() {
		Category category = new Category(MENU_ID, "Drinks", "Cold and hot drinks");

		assertEquals(MENU_ID, category.getMenuId());
		assertEquals("Drinks", category.getName());
		assertEquals("Cold and hot drinks", category.getDescription());
	}

	@Test
	void stripsNameAndDescription() {
		Category category = new Category(MENU_ID, "  Desserts ", " Sweet things ");

		assertEquals("Desserts", category.getName());
		assertEquals("Sweet things", category.getDescription());
	}

	@Test
	void allowsMissingDescription() {
		Category category = new Category(MENU_ID, "Starters", null);

		assertNull(category.getDescription());
	}

	@Test
	void rejectsBlankName() {
		InvalidCategoryException exception = assertThrows(InvalidCategoryException.class,
				() -> new Category(MENU_ID, "   ", "x"));

		assertEquals("The category name is required", exception.getMessage());
	}

	@Test
	void rejectsMissingName() {
		assertThrows(InvalidCategoryException.class, () -> new Category(MENU_ID, null, "x"));
	}

	@Test
	void rejectsMissingMenu() {
		InvalidCategoryException exception = assertThrows(InvalidCategoryException.class,
				() -> new Category(null, "Drinks", "x"));

		assertEquals("A category must belong to a menu", exception.getMessage());
	}

	@Test
	void acceptsNameAndDescriptionAtMaxLength() {
		String name = "n".repeat(Category.NAME_MAX_LENGTH);
		String description = "d".repeat(Category.DESCRIPTION_MAX_LENGTH);

		Category category = new Category(MENU_ID, name, description);

		assertEquals(name, category.getName());
		assertEquals(description, category.getDescription());
	}

	@Test
	void rejectsNameLongerThanMaxLength() {
		String name = "n".repeat(Category.NAME_MAX_LENGTH + 1);

		assertThrows(InvalidCategoryException.class, () -> new Category(MENU_ID, name, "x"));
	}

	@Test
	void rejectsDescriptionLongerThanMaxLength() {
		String description = "d".repeat(Category.DESCRIPTION_MAX_LENGTH + 1);

		assertThrows(InvalidCategoryException.class, () -> new Category(MENU_ID, "Drinks", description));
	}

	@Test
	void updateChangesDetailsButKeepsMenu() {
		Category category = new Category(MENU_ID, "Drinks", "Old");

		category.update("Cold drinks", "New");

		assertEquals("Cold drinks", category.getName());
		assertEquals("New", category.getDescription());
		assertEquals(MENU_ID, category.getMenuId());
	}

	@Test
	void updateRejectsBlankNameAndKeepsPreviousValues() {
		Category category = new Category(MENU_ID, "Drinks", "Old");

		assertThrows(InvalidCategoryException.class, () -> category.update(" ", "New"));

		assertEquals("Drinks", category.getName());
		assertEquals("Old", category.getDescription());
	}

	@Test
	void updateRejectsTooLongDescriptionAndKeepsPreviousValues() {
		Category category = new Category(MENU_ID, "Drinks", "Old");
		String description = "d".repeat(Category.DESCRIPTION_MAX_LENGTH + 1);

		assertThrows(InvalidCategoryException.class, () -> category.update("Cold drinks", description));

		assertEquals("Drinks", category.getName());
		assertEquals("Old", category.getDescription());
	}

	@Test
	void partialUpdateKeepsOmittedFieldsAndAllowsClearingDescription() {
		Category category = new Category(MENU_ID, "Drinks", "Old");

		category.update(null, false, "", true);

		assertEquals("Drinks", category.getName());
		assertNull(category.getDescription());
	}

	@Test
	void partialUpdateValidatesBeforeChangingAnyField() {
		Category category = new Category(MENU_ID, "Drinks", "Old");
		String description = "d".repeat(Category.DESCRIPTION_MAX_LENGTH + 1);

		assertThrows(InvalidCategoryException.class,
				() -> category.update("Cold drinks", true, description, true));

		assertEquals("Drinks", category.getName());
		assertEquals("Old", category.getDescription());
	}

}
