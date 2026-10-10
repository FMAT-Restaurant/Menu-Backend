package com.fmatrestaurant.menu.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.EntryStatus;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;

/**
 * Catalog entry use cases (REQ-MENU-ENTRY-001, REQ-MENU-ENTRY-002, REQ-MENU-ENTRY-003).
 *
 * <p>Each write runs in a transaction, so a rejected request leaves no partial changes.
 */
@Service
public class CatalogEntryService {

	/** Category filter value that selects the entries without categories. */
	public static final String UNCATEGORIZED = "__uncategorized__";

	private final CatalogEntryRepository entryRepository;

	private final CategoryRepository categoryRepository;

	private final ImageRepository imageRepository;

	public CatalogEntryService(CatalogEntryRepository entryRepository, CategoryRepository categoryRepository,
			ImageRepository imageRepository) {
		this.entryRepository = entryRepository;
		this.categoryRepository = categoryRepository;
		this.imageRepository = imageRepository;
	}

	/**
	 * Creates an entry in the menu. It always starts {@link EntryStatus#INACTIVE}.
	 *
	 * @throws InvalidFieldException if a value is missing or invalid, a category does not exist or
	 *         belongs to another menu, or the image does not exist
	 */
	@Transactional
	public CatalogEntry create(String brandName, String description, List<UUID> categoryIds, UUID imageId) {
		CatalogEntry entry = new CatalogEntry(CategoryService.DEFAULT_MENU_ID, brandName, description,
				existingImage(imageId), categories(categoryIds));
		return entryRepository.save(entry);
	}

	/**
	 * @throws CatalogEntryNotFoundException if the entry does not exist
	 */
	@Transactional(readOnly = true)
	public CatalogEntry get(UUID id) {
		return find(id);
	}

	/**
	 * Administrative search, regardless of whether the entries can be published.
	 *
	 * @param categoryFilter a category id or {@link #UNCATEGORIZED}
	 * @param status when {@code null}, archived entries are left out
	 * @param page page number, starting at 1
	 * @throws InvalidFieldException if the category filter, the page or the page size are not valid
	 */
	@Transactional(readOnly = true)
	public Page<CatalogEntry> list(String q, String categoryFilter, EntryStatus status, int page, int pageSize) {
		if (page < 1) {
			throw new InvalidFieldException("page", "The page must be 1 or greater");
		}
		if (pageSize < 1) {
			throw new InvalidFieldException("pageSize", "The page size must be 1 or greater");
		}
		boolean uncategorized = UNCATEGORIZED.equals(categoryFilter);
		UUID categoryId = categoryFilter == null || uncategorized ? null : categoryId(categoryFilter);
		return entryRepository.findAll(
				CatalogEntryRepository.search(CategoryService.DEFAULT_MENU_ID, q, categoryId, uncategorized, status),
				PageRequest.of(page - 1, pageSize, Sort.by("brandName", "id")));
	}

	/**
	 * Applies the given changes; {@code null} values keep the current ones and the categories, when
	 * given, replace the current ones.
	 *
	 * @param expectedVersion the version the client read
	 * @throws CatalogEntryNotFoundException if the entry does not exist
	 * @throws StaleCatalogEntryException if the entry changed since the expected version
	 * @throws InvalidFieldException if a value or the status transition is not valid
	 */
	@Transactional
	public CatalogEntry update(UUID id, long expectedVersion, String brandName, String description,
			EntryStatus status, List<UUID> categoryIds, UUID imageId) {
		CatalogEntry entry = find(id);
		if (entry.getVersion() != expectedVersion) {
			throw new StaleCatalogEntryException(id);
		}
		entry.update(brandName, description, existingImage(imageId), categories(categoryIds), status);
		return entryRepository.saveAndFlush(entry);
	}

	/** Not transactional: it runs in the transaction of the caller. */
	private CatalogEntry find(UUID id) {
		return entryRepository.findById(id).orElseThrow(() -> new CatalogEntryNotFoundException(id));
	}

	private List<Category> categories(List<UUID> ids) {
		if (ids == null) {
			return null;
		}
		Set<UUID> uniqueIds = new HashSet<>(ids);
		if (uniqueIds.contains(null)) {
			throw new InvalidFieldException("/categoryIds", "The category ids must not be null");
		}
		List<Category> found = categoryRepository.findAllById(uniqueIds);
		if (found.size() != uniqueIds.size()) {
			throw new InvalidFieldException("/categoryIds", "Some of the categories do not exist");
		}
		return found;
	}

	private UUID existingImage(UUID imageId) {
		if (imageId != null && !imageRepository.existsById(imageId)) {
			throw new InvalidFieldException("/imageId", "The image " + imageId + " does not exist");
		}
		return imageId;
	}

	private static UUID categoryId(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw new InvalidFieldException("categoryId", "The category filter must be a UUID or " + UNCATEGORIZED);
		}
	}

}
