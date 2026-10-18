package com.fmatrestaurant.menu.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A recipe of the recipe library (BR-MENU-020). Its definitions are kept as immutable
 * {@link RecipeRevision revisions}; this entity only knows which one is the current one. Its
 * version is the validator of the HTTP ETag and is not the revision of the domain.
 */
@Entity
@Table(name = "recipe")
public class Recipe {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	private Long version;

	@Column(name = "current_revision", nullable = false)
	private int currentRevision;

	/** Removed from the library, while its published revisions remain available as history. */
	@Column(nullable = false)
	private boolean deleted;

	/** A new recipe starts at revision 1. */
	public Recipe() {
		this.currentRevision = 1;
	}

	/** Moves to the next revision and returns its number. */
	public int publishNextRevision() {
		currentRevision++;
		return currentRevision;
	}

	public UUID getId() {
		return id;
	}

	public Long getVersion() {
		return version;
	}

	public int getCurrentRevision() {
		return currentRevision;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void delete() {
		this.deleted = true;
	}

}
