package com.fmatrestaurant.menu.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;

/**
 * Casos de uso de categorías (REQ-MENU-CAT-001).
 *
 * <p>De momento existe un solo menú, por lo que su id es simbólico y se
 * asigna aquí.
 */
@Service
public class CategoryService {

	/** Id simbólico del único menú existente por ahora. */
	public static final Long DEFAULT_MENU_ID = 1L;

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public List<Category> list() {
		return categoryRepository.findByMenuId(DEFAULT_MENU_ID);
	}

	public Category create(String name, String description) {
		Category category = new Category(DEFAULT_MENU_ID, name, description);
		return categoryRepository.save(category);
	}

	public Category update(Long id, String name, String description) {
		Category category = categoryRepository.findById(id)
				.orElseThrow(() -> new CategoryNotFoundException(id));
		category.update(name, description);
		return categoryRepository.save(category);
	}

}
