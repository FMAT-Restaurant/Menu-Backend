package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;

class CategoryServiceTest {

	private CategoryRepository repository;

	private CategoryService service;

	@BeforeEach
	void setUp() {
		repository = mock(CategoryRepository.class);
		service = new CategoryService(repository);
		when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void listReturnsCategoriesOfTheMenu() {
		List<Category> categories = List.of(new Category(CategoryService.DEFAULT_MENU_ID, "Bebidas", "x"));
		when(repository.findByMenuId(CategoryService.DEFAULT_MENU_ID)).thenReturn(categories);

		assertEquals(categories, service.list());
	}

	@Test
	void createAssignsTheMenuAndKeepsDetails() {
		Category created = service.create("Postres", "Dulces");

		assertEquals(CategoryService.DEFAULT_MENU_ID, created.getMenuId());
		assertEquals("Postres", created.getName());
		assertEquals("Dulces", created.getDescription());
		verify(repository).save(created);
	}

	@Test
	void createRejectsBlankName() {
		assertThrows(IllegalArgumentException.class, () -> service.create(" ", "x"));
		verify(repository, never()).save(any(Category.class));
	}

	@Test
	void updateChangesNameAndDescription() {
		Category existing = new Category(CategoryService.DEFAULT_MENU_ID, "Bebidas", "Viejo");
		when(repository.findById(7L)).thenReturn(Optional.of(existing));

		Category updated = service.update(7L, "Bebidas frías", "Nuevo");

		assertEquals("Bebidas frías", updated.getName());
		assertEquals("Nuevo", updated.getDescription());
		assertEquals(CategoryService.DEFAULT_MENU_ID, updated.getMenuId());
		verify(repository).save(existing);
	}

	@Test
	void updateFailsWhenCategoryDoesNotExist() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(CategoryNotFoundException.class, () -> service.update(99L, "x", "y"));
		verify(repository, never()).save(any(Category.class));
	}

}
