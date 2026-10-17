package com.fmatrestaurant.menu.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A published definition of a recipe: its text and its ingredient lines (REQ-MENU-REC-001,
 * REQ-MENU-REC-003). It never changes after it is created, so the options that reference it by
 * recipe and revision keep resolving the same definition (INV-MENU-008).
 */
@Entity
@Table(name = "recipe_revision", uniqueConstraints = @UniqueConstraint(name = "uk_recipe_revision",
		columnNames = { "recipe_id", "revision" }))
public class RecipeRevision {

	/** Maximum length of the name, matching the database column. */
	public static final int NAME_MAX_LENGTH = 255;

	/** Maximum length of the description, matching the database column. */
	public static final int DESCRIPTION_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "recipe_id", nullable = false)
	private Recipe recipe;

	@Column(nullable = false)
	private int revision;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
	private String description;

	@Column(nullable = false, columnDefinition = "text")
	private String instructions;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "revision_id", nullable = false)
	@OrderColumn(name = "position")
	private List<ComponentIngredient> ingredients = new ArrayList<>();

	protected RecipeRevision() {
		// Required by JPA.
	}

	/**
	 * @throws InvalidFieldException if a text is missing, blank or too long, or there are no ingredients
	 */
	public RecipeRevision(Recipe recipe, int revision, String name, String description, String instructions,
			List<ComponentIngredient> ingredients) {
		this.name = Fields.text("/name", "recipe name", name, NAME_MAX_LENGTH);
		this.description = Fields.text("/description", "recipe description", description, DESCRIPTION_MAX_LENGTH);
		this.instructions = Fields.text("/instructions", "recipe instructions", instructions, Integer.MAX_VALUE);
		if (ingredients == null || ingredients.isEmpty()) {
			throw new InvalidFieldException("/supplies", "A recipe must have at least one ingredient");
		}
		this.recipe = recipe;
		this.revision = revision;
		this.ingredients.addAll(ingredients);
	}

	public UUID getId() {
		return id;
	}

	public Recipe getRecipe() {
		return recipe;
	}

	public int getRevision() {
		return revision;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getInstructions() {
		return instructions;
	}

	/** The ingredient lines, in the order they were sent. */
	public List<ComponentIngredient> getIngredients() {
		return List.copyOf(ingredients);
	}

}
