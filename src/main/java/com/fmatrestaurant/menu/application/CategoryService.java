package com.fmatrestaurant.menu.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.InvalidCategoryException;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;

/**
 * Category use cases (REQ-MENU-CAT-001).
 *
 * <p>For now there is a single menu, so its id is symbolic and is assigned here.
 */
@Service
public class CategoryService {

	/** Symbolic id of the only menu that exists for now. */
	public static final Long DEFAULT_MENU_ID = 1L;

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public List<Category> list() {
		return categoryRepository.findByMenuId(DEFAULT_MENU_ID);
	}

	/**
	 * Creates a category in the menu.
	 *
	 * @throws InvalidCategoryException if the name or the description are not valid
	 */
	public Category create(String name, String description) {
		Category category = new Category(DEFAULT_MENU_ID, name, description);
		return categoryRepository.save(category);
	}

	/**
	 * Updates the name and the description of a category.
	 *
	 * @throws InvalidCategoryException if the id, the name or the description are not valid
	 * @throws CategoryNotFoundException if the category does not exist
	 */
	public Category update(Long id, String name, String description) {
		if (id == null) {
			throw new InvalidCategoryException("The category id is required");
		}
		Category category = categoryRepository.findById(id)
				.orElseThrow(() -> new CategoryNotFoundException(id));
		category.update(name, description);
		return categoryRepository.save(category);
	}

}
