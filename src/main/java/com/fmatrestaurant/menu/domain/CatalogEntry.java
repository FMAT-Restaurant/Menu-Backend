package com.fmatrestaurant.menu.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Commercial identity of a product of the menu (BR-MENU-002). It belongs to a menu and is
 * classified only with categories of that same menu (INV-MENU-001).
 *
 * <p>Its status is administrative (BR-MENU-003). Whether it is published is derived from that
 * status and its offers (BR-MENU-004); there is no separate visibility attribute.
 */
@Entity
@Table(name = "catalog_entry")
public class CatalogEntry {

	/** Maximum length of the brand name, matching the database column. */
	public static final int BRAND_NAME_MAX_LENGTH = 255;

	/** Maximum length of the description, matching the database column. */
	public static final int DESCRIPTION_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	private Long version;

	@Column(name = "menu_id", nullable = false)
	private Long menuId;

	@Column(name = "brand_name", nullable = false, length = BRAND_NAME_MAX_LENGTH)
	private String brandName;

	@Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
	private String description;

	@Column(name = "image_id", nullable = false)
	private UUID imageId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private EntryStatus status;

	@ManyToMany
	@JoinTable(name = "catalog_entry_category",
			joinColumns = @JoinColumn(name = "entry_id"),
			inverseJoinColumns = @JoinColumn(name = "category_id"))
	private Set<Category> categories = new HashSet<>();

	protected CatalogEntry() {
		// Required by JPA.
	}

	/**
	 * Creates an entry. Every new entry starts {@link EntryStatus#INACTIVE}.
	 *
	 * @throws InvalidFieldException if any value is missing or invalid, or a category belongs to another menu
	 */
	public CatalogEntry(Long menuId, String brandName, String description, UUID imageId,
			Collection<Category> categories) {
		if (menuId == null) {
			throw new InvalidFieldException("/menuId", "A catalog entry must belong to a menu");
		}
		this.menuId = menuId;
		this.brandName = text("/brandName", "brand name", brandName, BRAND_NAME_MAX_LENGTH);
		this.description = text("/description", "description", description, DESCRIPTION_MAX_LENGTH);
		if (imageId == null) {
			throw new InvalidFieldException("/imageId", "The catalog entry image is required");
		}
		this.imageId = imageId;
		if (categories == null) {
			throw new InvalidFieldException("/categoryIds", "The catalog entry categories are required");
		}
		this.categories.addAll(sameMenu(categories));
		this.status = EntryStatus.INACTIVE;
	}

	/**
	 * Applies the given changes; a {@code null} value keeps the current one. The categories, when
	 * given, replace the current ones. If any value is invalid, nothing is modified.
	 *
	 * <p>Changing the status does not change the status of the offers.
	 *
	 * @throws InvalidFieldException if any value is invalid or the status transition is not allowed
	 */
	public void update(String brandName, String description, UUID imageId, Collection<Category> categories,
			EntryStatus status) {
		String newBrandName = brandName == null ? this.brandName
				: text("/brandName", "brand name", brandName, BRAND_NAME_MAX_LENGTH);
		String newDescription = description == null ? this.description
				: text("/description", "description", description, DESCRIPTION_MAX_LENGTH);
		Set<Category> newCategories = categories == null ? null : sameMenu(categories);
		if (status == EntryStatus.ACTIVE && this.status == EntryStatus.ARCHIVED) {
			throw new InvalidFieldException("/status",
					"An archived catalog entry must be unarchived to INACTIVE before it can be activated");
		}
		this.brandName = newBrandName;
		this.description = newDescription;
		if (imageId != null) {
			this.imageId = imageId;
		}
		// Replacing an equal set would still mark the collection dirty and bump the version.
		if (newCategories != null && !this.categories.equals(newCategories)) {
			this.categories.clear();
			this.categories.addAll(newCategories);
		}
		if (status != null) {
			this.status = status;
		}
	}

	private Set<Category> sameMenu(Collection<Category> categories) {
		for (Category category : categories) {
			if (!menuId.equals(category.getMenuId())) {
				throw new InvalidFieldException("/categoryIds",
						"The category " + category.getId() + " belongs to another menu");
			}
		}
		return new HashSet<>(categories);
	}

	private static String text(String path, String label, String value, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new InvalidFieldException(path, "The catalog entry " + label + " is required");
		}
		String stripped = value.strip();
		if (stripped.length() > maxLength) {
			throw new InvalidFieldException(path,
					"The catalog entry " + label + " must not exceed " + maxLength + " characters");
		}
		return stripped;
	}

	public UUID getId() {
		return id;
	}

	public Long getVersion() {
		return version;
	}

	public Long getMenuId() {
		return menuId;
	}

	public String getBrandName() {
		return brandName;
	}

	public String getDescription() {
		return description;
	}

	public UUID getImageId() {
		return imageId;
	}

	public EntryStatus getStatus() {
		return status;
	}

	public Set<Category> getCategories() {
		return Collections.unmodifiableSet(categories);
	}

}
