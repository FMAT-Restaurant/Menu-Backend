package com.fmatrestaurant.menu.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.domain.ComponentIngredient;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.Recipe;
import com.fmatrestaurant.menu.domain.RecipeRevision;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;

/**
 * Use cases of the recipe library (REQ-MENU-REC-001 to REQ-MENU-REC-003). Every edition of a recipe
 * publishes a new revision and leaves the previous ones untouched.
 *
 * <p>Each write runs in a transaction and validates the whole result, so a rejected request leaves
 * no partial definition or revision. The articles of the ingredients are confirmed with Inventory,
 * which owns them (BR-MENU-018).
 */
@Service
public class RecipeService {

	private final RecipeRepository recipeRepository;

	private final RecipeRevisionRepository revisionRepository;

	private final InventoryClient inventoryClient;

	public RecipeService(RecipeRepository recipeRepository, RecipeRevisionRepository revisionRepository,
			InventoryClient inventoryClient) {
		this.recipeRepository = recipeRepository;
		this.revisionRepository = revisionRepository;
		this.inventoryClient = inventoryClient;
	}

	/** An ingredient as received: the article it references and the quantity. */
	public record SupplyData(UUID inventoryItemId, BigDecimal quantity) {
	}

	/**
	 * Creates a recipe in the library, at revision 1.
	 *
	 * @throws InvalidFieldException if a value is missing or invalid, there are no ingredients, or an
	 *         article does not exist
	 * @throws org.springframework.web.client.RestClientException if Inventory cannot confirm the articles
	 */
	@Transactional
	public RecipeRevision create(String name, String description, String instructions, List<SupplyData> supplies) {
		Recipe recipe = new Recipe();
		RecipeRevision revision = new RecipeRevision(recipe, recipe.getCurrentRevision(), name, description,
				instructions, ingredients(supplies));
		recipeRepository.save(recipe);
		return revisionRepository.saveAndFlush(revision);
	}

	/**
	 * The current revision of every recipe, ordered by name.
	 *
	 * @param q text searched in the name and the description
	 * @param page page number, starting at 1
	 * @throws InvalidFieldException if the page or the page size are not valid
	 */
	@Transactional(readOnly = true)
	public Page<RecipeRevision> list(String q, int page, int pageSize) {
		Pages.check(page, pageSize);
		return revisionRepository.findAll(RecipeRevisionRepository.currentMatching(q),
				PageRequest.of(page - 1, pageSize, Sort.by("name", "id")));
	}

	/**
	 * @return the current revision of the recipe
	 * @throws RecipeNotFoundException if the recipe does not exist
	 */
	@Transactional(readOnly = true)
	public RecipeRevision get(UUID recipeId) {
		return current(find(recipeId));
	}

	/**
	 * The definition fixed by a reference to a recipe and one of its revisions, whether it is the
	 * current one or an earlier one.
	 *
	 * @throws RecipeNotFoundException if the recipe or the revision does not exist
	 */
	@Transactional(readOnly = true)
	public RecipeRevision getRevision(UUID recipeId, int revision) {
		return revisionRepository.findByRecipeIdAndRevision(recipeId, revision)
				.orElseThrow(() -> new RecipeNotFoundException(recipeId, revision));
	}

	/**
	 * Publishes a new revision with the given changes; a {@code null} value keeps the current one.
	 * The ingredients, when sent, replace the whole list. When nothing is sent, no revision is
	 * published.
	 *
	 * @param expectedVersion the version of the recipe the client read
	 * @throws RecipeNotFoundException if the recipe does not exist
	 * @throws StaleRecipeException if the recipe changed since the expected version
	 * @throws InvalidFieldException if a value is invalid; nothing is published
	 * @throws org.springframework.web.client.RestClientException if Inventory cannot confirm the articles
	 */
	@Transactional
	public RecipeRevision update(UUID recipeId, long expectedVersion, String name, String description,
			String instructions, List<SupplyData> supplies) {
		Recipe recipe = find(recipeId);
		long currentVersion = recipe.getVersion() == null ? 0 : recipe.getVersion();
		if (currentVersion != expectedVersion) {
			throw new StaleRecipeException(recipeId);
		}
		RecipeRevision current = current(recipe);
		if (name == null && description == null && instructions == null && supplies == null) {
			return current;
		}
		RecipeRevision next = new RecipeRevision(recipe, recipe.getCurrentRevision() + 1,
				name == null ? current.getName() : name,
				description == null ? current.getDescription() : description,
				instructions == null ? current.getInstructions() : instructions,
				supplies == null ? current.getIngredients().stream().map(ComponentIngredient::copy).toList()
						: ingredients(supplies));
		recipe.publishNextRevision();
		recipeRepository.saveAndFlush(recipe);
		return revisionRepository.saveAndFlush(next);
	}

	private Recipe find(UUID recipeId) {
		return recipeRepository.findById(recipeId).orElseThrow(() -> new RecipeNotFoundException(recipeId));
	}

	private RecipeRevision current(Recipe recipe) {
		return revisionRepository.findByRecipeIdAndRevision(recipe.getId(), recipe.getCurrentRevision())
				.orElseThrow(() -> new RecipeNotFoundException(recipe.getId(), recipe.getCurrentRevision()));
	}

	/** Inventory is consulted once per article in each operation. */
	private List<ComponentIngredient> ingredients(List<SupplyData> supplies) {
		if (supplies == null || supplies.isEmpty()) {
			throw new InvalidFieldException("/supplies", "A recipe must have at least one ingredient");
		}
		Map<UUID, InventoryItem> items = new HashMap<>();
		List<ComponentIngredient> result = new ArrayList<>();
		for (int i = 0; i < supplies.size(); i++) {
			SupplyData supply = supplies.get(i);
			try {
				if (supply == null) {
					throw new InvalidFieldException("", "The ingredient is required");
				}
				result.add(new ComponentIngredient(inventoryItem(supply.inventoryItemId(), items), supply.quantity()));
			}
			catch (InvalidFieldException e) {
				throw e.prefixed("/supplies/" + i);
			}
		}
		return result;
	}

	private InventoryItem inventoryItem(UUID id, Map<UUID, InventoryItem> items) {
		if (id == null) {
			throw new InvalidFieldException("/inventoryItemId", "The inventory item id is required");
		}
		return items.computeIfAbsent(id, key -> inventoryClient.find(key).orElseThrow(
				() -> new InvalidFieldException("/inventoryItemId", "The inventory item " + key + " does not exist")));
	}

}
