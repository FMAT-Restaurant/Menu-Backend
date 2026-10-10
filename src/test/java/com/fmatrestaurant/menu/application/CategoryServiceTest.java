package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.InvalidCategoryException;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;

class CategoryServiceTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

	private static final UUID MISSING_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

	private CategoryRepository repository;

	private CategoryService service;

	@BeforeEach
	void setUp() {
		repository = mock(CategoryRepository.class);
		service = new CategoryService(repository);
		when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void listReturnsTheCategoriesOfTheMenu() {
		List<Category> categories = List.of(new Category(CategoryService.DEFAULT_MENU_ID, "Drinks", "x"));
		when(repository.findByMenuId(CategoryService.DEFAULT_MENU_ID)).thenReturn(categories);

		assertEquals(categories, service.list());
	}

	@Test
	void listReturnsEmptyWhenThereAreNoCategories() {
		when(repository.findByMenuId(CategoryService.DEFAULT_MENU_ID)).thenReturn(List.of());

		assertTrue(service.list().isEmpty());
	}

	@Test
	void createAssignsTheMenuAndKeepsDetails() {
		Category created = service.create("Desserts", "Sweet things");

		assertEquals(CategoryService.DEFAULT_MENU_ID, created.getMenuId());
		assertEquals("Desserts", created.getName());
		assertEquals("Sweet things", created.getDescription());
		verify(repository).save(created);
	}

	@Test
	void createRejectsBlankNameWithoutSaving() {
		assertThrows(InvalidCategoryException.class, () -> service.create(" ", "x"));

		verify(repository, never()).save(any(Category.class));
	}

	@Test
	void createRejectsTooLongNameWithoutSaving() {
		String name = "n".repeat(Category.NAME_MAX_LENGTH + 1);

		assertThrows(InvalidCategoryException.class, () -> service.create(name, "x"));

		verify(repository, never()).save(any(Category.class));
	}

	@Test
	void createPropagatesRepositoryFailures() {
		doThrow(new DataIntegrityViolationException("constraint violated"))
				.when(repository).save(any(Category.class));

		assertThrows(DataIntegrityViolationException.class, () -> service.create("Drinks", "x"));
	}

	@Test
	void updateChangesNameAndDescription() {
		Category existing = new Category(CategoryService.DEFAULT_MENU_ID, "Drinks", "Old");
		when(repository.findById(ID)).thenReturn(Optional.of(existing));

		Category updated = service.update(ID, "Cold drinks", "New");

		assertEquals("Cold drinks", updated.getName());
		assertEquals("New", updated.getDescription());
		assertEquals(CategoryService.DEFAULT_MENU_ID, updated.getMenuId());
		verify(repository).save(existing);
	}

	@Test
	void updateFailsWhenCategoryDoesNotExist() {
		when(repository.findById(MISSING_ID)).thenReturn(Optional.empty());

		CategoryNotFoundException exception = assertThrows(CategoryNotFoundException.class,
				() -> service.update(MISSING_ID, "x", "y"));

		assertEquals("Category not found with id " + MISSING_ID, exception.getMessage());
		verify(repository, never()).save(any(Category.class));
	}

	@Test
	void updateRejectsMissingIdWithoutQueryingTheRepository() {
		assertThrows(InvalidCategoryException.class, () -> service.update(null, "x", "y"));

		verify(repository, never()).findById(any());
		verify(repository, never()).save(any(Category.class));
	}

	@Test
	void updateRejectsBlankNameWithoutSaving() {
		Category existing = new Category(CategoryService.DEFAULT_MENU_ID, "Drinks", "Old");
		when(repository.findById(ID)).thenReturn(Optional.of(existing));

		assertThrows(InvalidCategoryException.class, () -> service.update(ID, " ", "New"));

		assertEquals("Drinks", existing.getName());
		verify(repository, never()).save(any(Category.class));
	}

}
