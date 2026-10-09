package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CategoryNotFoundException;
import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.domain.Category;

/**
 * Integration test against PostgreSQL (Testcontainers). Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class CategoryRepositoryTest {

	@Autowired
	private CategoryRepository repository;

	@Autowired
	private CategoryService service;

	@Test
	void persistsAndFindsCategoriesByMenu() {
		repository.save(new Category(1L, "Drinks", "Cold and hot drinks"));
		repository.save(new Category(2L, "Another menu", null));

		List<Category> found = repository.findByMenuId(1L);

		assertEquals(1, found.size());
		assertNotNull(found.get(0).getId());
		assertEquals("Drinks", found.get(0).getName());
		assertEquals("Cold and hot drinks", found.get(0).getDescription());
	}

	@Test
	void serviceCreatesListsAndUpdates() {
		Category created = service.create("Desserts", "Sweet things");

		assertEquals(1, service.list().size());

		Category updated = service.update(created.getId(), "Cold desserts", "Ice cream");

		assertEquals("Cold desserts", updated.getName());
		assertEquals("Ice cream", service.list().get(0).getDescription());
	}

	@Test
	void serviceFailsToUpdateAMissingCategory() {
		assertThrows(CategoryNotFoundException.class, () -> service.update(999_999L, "x", "y"));
	}

}
