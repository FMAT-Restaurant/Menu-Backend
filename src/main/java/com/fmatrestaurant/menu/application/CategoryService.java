package com.fmatrestaurant.menu.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.InvalidCategoryException;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
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

	private final CatalogEntryRepository entryRepository;

	public CategoryService(CategoryRepository categoryRepository, CatalogEntryRepository entryRepository) {
		this.categoryRepository = categoryRepository;
		this.entryRepository = entryRepository;
	}

	@Transactional(readOnly = true)
	public List<Category> list() {
		return categoryRepository.findByMenuId(DEFAULT_MENU_ID);
	}

	/**
	 * Returns a category that belongs to the menu.
	 *
	 * @throws CategoryNotFoundException if the category does not exist in this menu
	 */
	@Transactional(readOnly = true)
	public Category get(UUID id) {
		return findInMenu(id);
	}

	/** Returns one page and the distinct entry count for each category on it. */
	@Transactional(readOnly = true)
	public CategoryPage list(int page, int pageSize) {
		if (page < 1) {
			throw new InvalidFieldException("page", "The page must be 1 or greater");
		}
		if (pageSize < 1) {
			throw new InvalidFieldException("pageSize", "The page size must be 1 or greater");
		}
		Page<Category> categories = categoryRepository.findByMenuId(DEFAULT_MENU_ID,
				PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
		Map<UUID, Long> entryCounts = entryCounts(categories.getContent().stream().map(Category::getId).toList());
		return new CategoryPage(categories, entryCounts);
	}

	/**
	 * Creates a category in the menu.
	 *
	 * @throws InvalidCategoryException if the name or the description are not valid
	 */
	@Transactional
	public Category create(String name, String description) {
		Category category = new Category(DEFAULT_MENU_ID, name, description);
		ensureNameAvailable(category.getMenuId(), category.getName(), null);
		return categoryRepository.saveAndFlush(category);
	}

	/** Updates both fields for service callers that provide a complete category. */
	@Transactional
	public Category update(UUID id, String name, String description) {
		Category category = findInMenu(id);
		ensureNameAvailable(category.getMenuId(), name, category.getId());
		category.update(name, description);
		return categoryRepository.saveAndFlush(category);
	}

	/**
	 * Applies a partial update if the client's category version is still current.
	 *
	 * @throws InvalidCategoryException if the id or supplied values are invalid
	 * @throws CategoryNotFoundException if the category does not exist in this menu
	 * @throws OptimisticLockingFailureException if the category changed since it was read
	 */
	@Transactional
	public Category update(UUID id, long expectedVersion, String name, boolean updateName,
			String description, boolean updateDescription) {
		Category category = findInMenu(id);
		long currentVersion = category.getVersion() == null ? 0 : category.getVersion();
		if (expectedVersion != currentVersion) {
			throw new OptimisticLockingFailureException("The category has changed since it was read");
		}
		if (updateName) {
			ensureNameAvailable(category.getMenuId(), name, category.getId());
		}
		category.update(name, updateName, description, updateDescription);
		return categoryRepository.saveAndFlush(category);
	}

	/**
	 * Deletes a category if the client's version is still current and removes its entry associations.
	 * Catalog entries themselves remain in the menu.
	 *
	 * @throws CategoryNotFoundException if the category does not exist in this menu
	 * @throws OptimisticLockingFailureException if the category changed since it was read
	 */
	@Transactional
	public Category delete(UUID id, long expectedVersion) {
		Category category = findInMenu(id);
		long currentVersion = category.getVersion() == null ? 0 : category.getVersion();
		if (expectedVersion != currentVersion) {
			throw new OptimisticLockingFailureException("The category has changed since it was read");
		}
		entryRepository.deleteCategoryAssociations(id);
		categoryRepository.delete(category);
		categoryRepository.flush();
		return category;
	}

	private void ensureNameAvailable(Long menuId, String name, UUID excludedId) {
		if (name == null) {
			return;
		}
		String normalizedName = name.strip();
		boolean exists = excludedId == null
				? categoryRepository.existsByMenuIdAndName(menuId, normalizedName)
				: categoryRepository.existsByMenuIdAndNameAndIdNot(menuId, normalizedName, excludedId);
		if (exists) {
			throw new InvalidCategoryException("/name", "A category with this name already exists in this menu");
		}
	}

	@Transactional(readOnly = true)
	public long entryCount(UUID categoryId) {
		return entryCounts(List.of(categoryId)).getOrDefault(categoryId, 0L);
	}

	private Map<UUID, Long> entryCounts(List<UUID> categoryIds) {
		if (categoryIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, Long> counts = new HashMap<>();
		entryRepository.countDistinctEntriesByCategory(DEFAULT_MENU_ID, categoryIds).forEach(
				count -> counts.put(count.getCategoryId(), count.getEntryCount()));
		return Map.copyOf(counts);
	}

	private Category findInMenu(UUID id) {
		if (id == null) {
			throw new InvalidCategoryException("/categoryId", "The category id is required");
		}
		Category category = categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
		if (!DEFAULT_MENU_ID.equals(category.getMenuId())) {
			throw new CategoryNotFoundException(id);
		}
		return category;
	}

	public record CategoryPage(Page<Category> page, Map<UUID, Long> entryCounts) {
	}

}
