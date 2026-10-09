package com.fmatrestaurant.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Category of a menu. It always belongs to a menu (INV-MENU-001).
 */
@Entity
@Table(name = "category")
public class Category {

	/** Maximum length of the name, matching the database column. */
	public static final int NAME_MAX_LENGTH = 255;

	/** Maximum length of the description, matching the database column. */
	public static final int DESCRIPTION_MAX_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "menu_id", nullable = false)
	private Long menuId;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(length = DESCRIPTION_MAX_LENGTH)
	private String description;

	protected Category() {
		// Required by JPA.
	}

	public Category(Long menuId, String name, String description) {
		if (menuId == null) {
			throw new InvalidCategoryException("A category must belong to a menu");
		}
		this.menuId = menuId;
		applyDetails(name, description);
	}

	/**
	 * Updates the name and the description. The menu the category belongs to does not change.
	 * If any value is invalid, nothing is modified.
	 */
	public void update(String name, String description) {
		applyDetails(name, description);
	}

	private void applyDetails(String name, String description) {
		if (name == null || name.isBlank()) {
			throw new InvalidCategoryException("The category name is required");
		}
		String strippedName = name.strip();
		if (strippedName.length() > NAME_MAX_LENGTH) {
			throw new InvalidCategoryException(
					"The category name must not exceed " + NAME_MAX_LENGTH + " characters");
		}
		String strippedDescription = description == null ? null : description.strip();
		if (strippedDescription != null && strippedDescription.length() > DESCRIPTION_MAX_LENGTH) {
			throw new InvalidCategoryException(
					"The category description must not exceed " + DESCRIPTION_MAX_LENGTH + " characters");
		}
		this.name = strippedName;
		this.description = strippedDescription;
	}

	public Long getId() {
		return id;
	}

	public Long getMenuId() {
		return menuId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

}
