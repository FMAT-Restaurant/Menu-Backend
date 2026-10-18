package com.fmatrestaurant.menu.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fmatrestaurant.menu.application.RecipeService;
import com.fmatrestaurant.menu.application.RecipeService.SupplyData;
import com.fmatrestaurant.menu.domain.ComponentIngredient;
import com.fmatrestaurant.menu.domain.RecipeRevision;

/**
 * HTTP operations for the recipe library. Business rules and persistence live in {@link RecipeService}.
 *
 * <p>The ETag is the version of the recipe and changes with every published revision; it is not the
 * revision number, which the responses report separately.
 */
@RestController
@RequestMapping("/api/v1/menu/recipes")
public class RecipeController {

	private final RecipeService recipeService;

	public RecipeController(RecipeService recipeService) {
		this.recipeService = recipeService;
	}

	@PostMapping
	public ResponseEntity<DataResponse<RecipeDetail>> create(@RequestBody RecipeRequest request) {
		RecipeRevision recipe = recipeService.create(request.name(), request.description(), request.instructions(),
				supplies(request.supplies()));
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(recipe))
				.body(new DataResponse<>(RecipeDetail.of(recipe)));
	}

	@GetMapping
	public RecipeListResponse list(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int pageSize) {
		Page<RecipeRevision> recipes = recipeService.list(q, page, pageSize);
		return new RecipeListResponse(recipes.getContent().stream().map(RecipeSummary::of).toList(),
				new PageMeta(page, pageSize, recipes.getTotalElements(), recipes.getTotalPages()));
	}

	@GetMapping("/{recipeId}")
	public ResponseEntity<DataResponse<RecipeDetail>> get(@PathVariable UUID recipeId) {
		RecipeRevision recipe = recipeService.get(recipeId);
		return ResponseEntity.ok().eTag(etag(recipe)).body(new DataResponse<>(RecipeDetail.of(recipe)));
	}

	/**
	 * Publishes a new revision. A field that is missing, or {@code null}, keeps its current value;
	 * the ingredients, when sent, replace the whole list.
	 */
	@PatchMapping("/{recipeId}")
	public ResponseEntity<DataResponse<RecipeDetail>> update(@PathVariable UUID recipeId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestBody RecipeRequest request) {
		RecipeRevision recipe = recipeService.update(recipeId, Revisions.strongVersion(ifMatch), request.name(),
				request.description(), request.instructions(),
				request.supplies() == null ? null : supplies(request.supplies()));
		return ResponseEntity.ok().eTag(etag(recipe)).body(new DataResponse<>(RecipeDetail.of(recipe)));
	}

	/** Removes an unused recipe from the library, preserving its published revisions. */
	@DeleteMapping("/{recipeId}")
	public ResponseEntity<Void> delete(@PathVariable UUID recipeId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
		long version = recipeService.delete(recipeId, Revisions.strongVersion(ifMatch));
		return ResponseEntity.noContent().eTag(Revisions.strong(version)).build();
	}

	private static List<SupplyData> supplies(List<SupplyRequest> supplies) {
		if (supplies == null) {
			return List.of();
		}
		return supplies.stream()
				.map(supply -> supply == null ? null : new SupplyData(supply.inventoryItemId(), supply.quantity()))
				.toList();
	}

	/** Strong, as the contract requires for If-Match. */
	private static String etag(RecipeRevision recipe) {
		return Revisions.strong(recipe.getRecipe().getVersion() == null ? 0 : recipe.getRecipe().getVersion());
	}

	/** Body of POST and PATCH; on PATCH, a missing value keeps the current one, and so does {@code null}. */
	public record RecipeRequest(String name, String description, String instructions,
			List<SupplyRequest> supplies) {
	}

	/** An ingredient as sent: the article, which Inventory confirms, and the quantity. */
	public record SupplyRequest(String inventoryItemId, BigDecimal quantity) {
	}

	public record InventoryItemData(String id, String name, String unit) {
	}

	public record Supply(String inventoryItemId, BigDecimal quantity, InventoryItemData inventoryItem) {

		static Supply of(ComponentIngredient ingredient) {
			return new Supply(ingredient.getInventoryItemId(), ingredient.getQuantity(),
					new InventoryItemData(ingredient.getInventoryItemId(), ingredient.getItemName(),
							ingredient.getUnit()));
		}

	}

	public record RecipeSummary(UUID id, int revision, String name, String description, List<Supply> supplies) {

		static RecipeSummary of(RecipeRevision recipe) {
			return new RecipeSummary(recipe.getRecipe().getId(), recipe.getRevision(), recipe.getName(),
					recipe.getDescription(), recipe.getIngredients().stream().map(Supply::of).toList());
		}

	}

	public record RecipeDetail(UUID id, int revision, String name, String description, String instructions,
			List<Supply> supplies) {

		static RecipeDetail of(RecipeRevision recipe) {
			return new RecipeDetail(recipe.getRecipe().getId(), recipe.getRevision(), recipe.getName(),
					recipe.getDescription(), recipe.getInstructions(),
					recipe.getIngredients().stream().map(Supply::of).toList());
		}
	}

	public record RecipeListResponse(List<RecipeSummary> data, PageMeta meta) {
	}

}
