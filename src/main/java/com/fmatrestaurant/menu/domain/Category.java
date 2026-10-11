package com.fmatrestaurant.menu.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Category of a menu. It always belongs to a menu (INV-MENU-001).
 */
@Entity
@Table(name = "category", uniqueConstraints = @UniqueConstraint(name = "uk_category_menu_name",
		columnNames = { "menu_id", "name" }))
public class Category {

	/** Maximum length of the name, matching the database column. */
	public static final int NAME_MAX_LENGTH = 255;

	/** Maximum length of the description, matching the database column. */
	public static final int DESCRIPTION_MAX_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	private Long version;

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
		update(name, true, description, true);
	}

	/**
	 * Applies only the supplied changes. Empty descriptions clear the optional description.
	 * Values are validated before either field is changed.
	 */
	public void update(String name, boolean updateName, String description, boolean updateDescription) {
		String newName = updateName ? validName(name) : this.name;
		String newDescription = updateDescription ? validDescription(description) : this.description;
		this.name = newName;
		this.description = newDescription;
	}

	private void applyDetails(String name, String description) {
		this.name = validName(name);
		this.description = validDescription(description);
	}

	private static String validName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidCategoryException("/name", "The category name is required");
		}
		String strippedName = name.strip();
		if (strippedName.length() > NAME_MAX_LENGTH) {
			throw new InvalidCategoryException("/name",
					"The category name must not exceed " + NAME_MAX_LENGTH + " characters");
		}
		return strippedName;
	}

	private static String validDescription(String description) {
		String strippedDescription = description == null ? null : description.strip();
		if (strippedDescription != null && strippedDescription.length() > DESCRIPTION_MAX_LENGTH) {
			throw new InvalidCategoryException("/description",
					"The category description must not exceed " + DESCRIPTION_MAX_LENGTH + " characters");
		}
		return strippedDescription == null || strippedDescription.isEmpty() ? null : strippedDescription;
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

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

}
