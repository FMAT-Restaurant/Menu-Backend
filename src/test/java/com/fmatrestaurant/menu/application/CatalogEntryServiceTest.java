package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.EntryStatus;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;

class CatalogEntryServiceTest {

	private static final UUID ENTRY_ID = UUID.randomUUID();

	private static final UUID IMAGE_ID = UUID.randomUUID();

	private static final UUID CATEGORY_ID = UUID.randomUUID();

	private static final long VERSION = 3;

	private final Category burgers = new Category(CategoryService.DEFAULT_MENU_ID, "Burgers", null);

	private CatalogEntryRepository entryRepository;

	private CategoryRepository categoryRepository;

	private ImageRepository imageRepository;

	private CatalogEntryService service;

	@BeforeEach
	void setUp() {
		entryRepository = mock(CatalogEntryRepository.class);
		categoryRepository = mock(CategoryRepository.class);
		imageRepository = mock(ImageRepository.class);
		service = new CatalogEntryService(entryRepository, categoryRepository, imageRepository,
				mock(CatalogOfferRepository.class));
		when(entryRepository.save(any(CatalogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(entryRepository.saveAndFlush(any(CatalogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(imageRepository.existsById(IMAGE_ID)).thenReturn(true);
		when(categoryRepository.findAllById(Set.of(CATEGORY_ID))).thenReturn(List.of(burgers));
	}

	@Test
	void createRegistersAnInactiveEntryInTheMenu() {
		CatalogEntry entry = service.create("Burger", "Tasty", List.of(CATEGORY_ID, CATEGORY_ID), IMAGE_ID);

		assertEquals(EntryStatus.INACTIVE, entry.getStatus());
		assertEquals(CategoryService.DEFAULT_MENU_ID, entry.getMenuId());
		assertEquals(Set.of(burgers), entry.getCategories());
		verify(entryRepository).save(entry);
	}

	@Test
	void createRejectsUnknownCategoriesWithoutSaving() {
		when(categoryRepository.findAllById(any())).thenReturn(List.of());

		assertPath("/categoryIds", () -> service.create("Burger", "Tasty", List.of(UUID.randomUUID()), IMAGE_ID));

		verify(entryRepository, never()).save(any(CatalogEntry.class));
	}

	@Test
	void createRejectsNullCategoryIdsWithoutSaving() {
		List<UUID> ids = new ArrayList<>();
		ids.add(null);

		assertPath("/categoryIds", () -> service.create("Burger", "Tasty", ids, IMAGE_ID));

		verify(entryRepository, never()).save(any(CatalogEntry.class));
	}

	@Test
	void createRejectsMissingCategoriesWithoutSaving() {
		assertPath("/categoryIds", () -> service.create("Burger", "Tasty", null, IMAGE_ID));

		verify(entryRepository, never()).save(any(CatalogEntry.class));
	}

	@Test
	void updateWithoutCategoriesKeepsTheCurrentOnes() {
		CatalogEntry entry = storedEntry();
		entry.update(null, null, null, List.of(burgers), null);
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

		service.update(ENTRY_ID, VERSION, null, null, EntryStatus.ACTIVE, null, null);

		assertEquals(Set.of(burgers), entry.getCategories());
		verify(categoryRepository, never()).findAllById(any());
	}

	@Test
	void createRejectsAnUnknownImageWithoutSaving() {
		assertPath("/imageId", () -> service.create("Burger", "Tasty", List.of(), UUID.randomUUID()));

		verify(entryRepository, never()).save(any(CatalogEntry.class));
	}

	@Test
	void updateAppliesTheChanges() {
		CatalogEntry entry = storedEntry();
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

		service.update(ENTRY_ID, VERSION, "Big burger", null, EntryStatus.ACTIVE, List.of(CATEGORY_ID), null);

		assertEquals("Big burger", entry.getBrandName());
		assertEquals("Tasty", entry.getDescription());
		assertEquals(EntryStatus.ACTIVE, entry.getStatus());
		assertEquals(Set.of(burgers), entry.getCategories());
		verify(entryRepository).saveAndFlush(entry);
	}

	@Test
	void updateRejectsAStaleVersionWithoutChanges() {
		CatalogEntry entry = storedEntry();
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

		assertThrows(StaleCatalogEntryException.class,
				() -> service.update(ENTRY_ID, VERSION + 1, "Big burger", null, null, null, null));

		assertEquals("Burger", entry.getBrandName());
		verify(entryRepository, never()).saveAndFlush(any(CatalogEntry.class));
	}

	@Test
	void updateRejectsAnUnknownImageAndKeepsTheCurrentOne() {
		CatalogEntry entry = storedEntry();
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

		assertPath("/imageId",
				() -> service.update(ENTRY_ID, VERSION, "Big burger", null, null, null, UUID.randomUUID()));

		assertEquals(IMAGE_ID, entry.getImageId());
		assertEquals("Burger", entry.getBrandName());
		verify(entryRepository, never()).saveAndFlush(any(CatalogEntry.class));
	}

	@Test
	void updateFailsWhenTheEntryDoesNotExist() {
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.empty());

		assertThrows(CatalogEntryNotFoundException.class,
				() -> service.update(ENTRY_ID, 0, "x", null, null, null, null));
	}

	@Test
	void listRejectsInvalidPagingAndFilters() {
		assertPath("page", () -> service.list(null, null, null, 0, 12));
		assertPath("pageSize", () -> service.list(null, null, null, 1, 0));
		assertPath("categoryId", () -> service.list(null, "not-a-uuid", null, 1, 12));
	}

	/** An entry as loaded by JPA, with the version it assigns. */
	private static CatalogEntry storedEntry() {
		CatalogEntry entry = new CatalogEntry(CategoryService.DEFAULT_MENU_ID, "Burger", "Tasty", IMAGE_ID, List.of());
		ReflectionTestUtils.setField(entry, "version", VERSION);
		return entry;
	}

	private static void assertPath(String path, Runnable action) {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class, action::run);
		assertEquals(path, exception.getPath());
	}

}
