package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.domain.Category;

/**
 * Prueba de integración contra PostgreSQL (Testcontainers). Requiere Docker.
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
		repository.save(new Category(1L, "Bebidas", "Frías y calientes"));
		repository.save(new Category(2L, "Otro menú", null));

		List<Category> found = repository.findByMenuId(1L);

		assertEquals(1, found.size());
		assertNotNull(found.get(0).getId());
		assertEquals("Bebidas", found.get(0).getName());
		assertEquals("Frías y calientes", found.get(0).getDescription());
	}

	@Test
	void serviceCreatesListsAndUpdates() {
		Category created = service.create("Postres", "Dulces");

		assertEquals(1, service.list().size());

		Category updated = service.update(created.getId(), "Postres fríos", "Helados");

		assertEquals("Postres fríos", updated.getName());
		assertEquals("Helados", service.list().get(0).getDescription());
	}

}
