package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogEntryTest {

	private static final Long MENU_ID = 1L;

	private static final UUID IMAGE_ID = UUID.randomUUID();

	private final Category burgers = new Category(MENU_ID, "Burgers", null);

	private final Category specials = new Category(MENU_ID, "Specials", null);

	private CatalogEntry entry() {
		return new CatalogEntry(MENU_ID, "Hawaiian burger", "Pineapple and ham", IMAGE_ID, List.of(burgers));
	}

	@Test
	void newEntryStartsInactiveAndKeepsItsData() {
		CatalogEntry entry = new CatalogEntry(MENU_ID, "  Hawaiian burger ", " Pineapple and ham ", IMAGE_ID,
				List.of(burgers, specials));

		assertEquals(EntryStatus.INACTIVE, entry.getStatus());
		assertEquals(MENU_ID, entry.getMenuId());
		assertEquals("Hawaiian burger", entry.getBrandName());
		assertEquals("Pineapple and ham", entry.getDescription());
		assertEquals(IMAGE_ID, entry.getImageId());
		assertEquals(Set.of(burgers, specials), entry.getCategories());
	}

	@Test
	void classifyingInSeveralCategoriesDoesNotDuplicateThem() {
		CatalogEntry entry = new CatalogEntry(MENU_ID, "Burger", "x", IMAGE_ID, List.of(burgers, burgers));

		assertEquals(1, entry.getCategories().size());
	}

	@Test
	void allowsAnEntryWithoutCategories() {
		assertTrue(new CatalogEntry(MENU_ID, "Burger", "x", IMAGE_ID, List.of()).getCategories().isEmpty());
	}

	@Test
	void rejectsMissingData() {
		assertPath("/menuId", () -> new CatalogEntry(null, "Burger", "x", IMAGE_ID, List.of()));
		assertPath("/brandName", () -> new CatalogEntry(MENU_ID, " ", "x", IMAGE_ID, List.of()));
		assertPath("/description", () -> new CatalogEntry(MENU_ID, "Burger", null, IMAGE_ID, List.of()));
		assertPath("/imageId", () -> new CatalogEntry(MENU_ID, "Burger", "x", null, List.of()));
		assertPath("/categoryIds", () -> new CatalogEntry(MENU_ID, "Burger", "x", IMAGE_ID, null));
	}

	@Test
	void rejectsTooLongTexts() {
		String name = "n".repeat(CatalogEntry.BRAND_NAME_MAX_LENGTH + 1);
		String description = "d".repeat(CatalogEntry.DESCRIPTION_MAX_LENGTH + 1);

		assertPath("/brandName", () -> new CatalogEntry(MENU_ID, name, "x", IMAGE_ID, List.of()));
		assertPath("/description", () -> new CatalogEntry(MENU_ID, "Burger", description, IMAGE_ID, List.of()));
	}

	@Test
	void rejectsCategoriesOfAnotherMenu() {
		Category foreign = new Category(2L, "Foreign", null);

		assertPath("/categoryIds", () -> new CatalogEntry(MENU_ID, "Burger", "x", IMAGE_ID, List.of(foreign)));
	}

	@Test
	void updateKeepsOmittedValuesAndReplacesCategories() {
		CatalogEntry entry = entry();

		entry.update(null, "New description", null, List.of(specials), null);

		assertEquals("Hawaiian burger", entry.getBrandName());
		assertEquals("New description", entry.getDescription());
		assertEquals(IMAGE_ID, entry.getImageId());
		assertEquals(Set.of(specials), entry.getCategories());
		assertEquals(EntryStatus.INACTIVE, entry.getStatus());
	}

	@Test
	void updateReplacesTheImage() {
		CatalogEntry entry = entry();
		UUID newImage = UUID.randomUUID();

		entry.update(null, null, newImage, null, null);

		assertEquals(newImage, entry.getImageId());
		assertEquals(Set.of(burgers), entry.getCategories());
	}

	@Test
	void rejectedUpdateChangesNothing() {
		CatalogEntry entry = entry();
		Category foreign = new Category(2L, "Foreign", null);

		assertPath("/categoryIds",
				() -> entry.update("New name", "New", UUID.randomUUID(), List.of(foreign), EntryStatus.ACTIVE));
		assertPath("/description",
				() -> entry.update("New name", " ", UUID.randomUUID(), List.of(specials), EntryStatus.ACTIVE));

		assertEquals("Hawaiian burger", entry.getBrandName());
		assertEquals("Pineapple and ham", entry.getDescription());
		assertEquals(IMAGE_ID, entry.getImageId());
		assertEquals(Set.of(burgers), entry.getCategories());
		assertEquals(EntryStatus.INACTIVE, entry.getStatus());
	}

	@ParameterizedTest
	@CsvSource({
		"INACTIVE, ACTIVE", "ACTIVE, INACTIVE", "ACTIVE, ARCHIVED", "INACTIVE, ARCHIVED", "ARCHIVED, INACTIVE",
		"ACTIVE, ACTIVE", "INACTIVE, INACTIVE", "ARCHIVED, ARCHIVED" })
	void allowedTransitions(EntryStatus from, EntryStatus to) {
		CatalogEntry entry = entryIn(from);

		entry.update(null, null, null, null, to);

		assertEquals(to, entry.getStatus());
	}

	@Test
	void archivedEntryCannotBeActivatedDirectly() {
		CatalogEntry entry = entryIn(EntryStatus.ARCHIVED);

		assertPath("/status", () -> entry.update("Renamed", null, null, null, EntryStatus.ACTIVE));

		assertEquals(EntryStatus.ARCHIVED, entry.getStatus());
		assertEquals("Hawaiian burger", entry.getBrandName());
	}

	@Test
	void unarchivedEntryNeedsAnExplicitActivation() {
		CatalogEntry entry = entryIn(EntryStatus.ARCHIVED);

		entry.update(null, null, null, null, EntryStatus.INACTIVE);
		assertDoesNotThrow(() -> entry.update(null, null, null, null, EntryStatus.ACTIVE));

		assertEquals(EntryStatus.ACTIVE, entry.getStatus());
	}

	private CatalogEntry entryIn(EntryStatus status) {
		CatalogEntry entry = entry();
		entry.update(null, null, null, null, status);
		return entry;
	}

	private static void assertPath(String path, Runnable action) {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class, action::run);
		assertEquals(path, exception.getPath());
	}

}
