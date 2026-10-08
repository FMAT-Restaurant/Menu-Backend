package com.fmatrestaurant.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categoría reutilizable de un menú. Pertenece siempre a un menú (INV-MENU-001).
 */
@Entity
@Table(name = "category")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "menu_id", nullable = false)
	private Long menuId;

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	protected Category() {
		// Requerido por JPA.
	}

	public Category(Long menuId, String name, String description) {
		if (menuId == null) {
			throw new IllegalArgumentException("La categoría debe pertenecer a un menú");
		}
		this.menuId = menuId;
		applyDetails(name, description);
	}

	/**
	 * Actualiza nombre y descripción. La pertenencia al menú no cambia.
	 */
	public void update(String name, String description) {
		applyDetails(name, description);
	}

	private void applyDetails(String name, String description) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
		}
		this.name = name.strip();
		this.description = description == null ? null : description.strip();
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
