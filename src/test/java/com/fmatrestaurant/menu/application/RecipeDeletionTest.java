package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fmatrestaurant.menu.domain.Recipe;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;

class RecipeDeletionTest {

	private static final UUID RECIPE_ID = UUID.randomUUID();

	private static final long VERSION = 3;

	private RecipeRepository recipeRepository;

	private RecipeRevisionRepository revisionRepository;

	private CatalogOfferRepository offerRepository;

	private InventoryClient inventoryClient;

	private RecipeService service;

	@BeforeEach
	void setUp() {
		recipeRepository = mock(RecipeRepository.class);
		revisionRepository = mock(RecipeRevisionRepository.class);
		offerRepository = mock(CatalogOfferRepository.class);
		inventoryClient = mock(InventoryClient.class);
		service = new RecipeService(recipeRepository, revisionRepository, inventoryClient, offerRepository);
	}

	@Test
	void removesTheCurrentDefinitionWithoutTouchingHistoryOrInventory() {
		Recipe recipe = existing();
		when(offerRepository.findIdsReferencingRecipe(RECIPE_ID)).thenReturn(List.of());
		when(recipeRepository.saveAndFlush(recipe)).thenAnswer(_ -> {
			ReflectionTestUtils.setField(recipe, "version", VERSION + 1);
			return recipe;
		});

		assertEquals(VERSION + 1, service.delete(RECIPE_ID, VERSION));

		assertTrue(recipe.isDeleted());
		assertEquals(1, recipe.getCurrentRevision());
		verify(recipeRepository).saveAndFlush(recipe);
		verify(recipeRepository, never()).delete(any());
		verifyNoInteractions(revisionRepository, inventoryClient);
	}

	@Test
	void rejectsARecipeInUseWithoutChangingItAndReportsTheOffers() {
		Recipe recipe = existing();
		List<UUID> offers = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(offerRepository.findIdsReferencingRecipe(RECIPE_ID)).thenReturn(offers);

		RecipeInUseException error = assertThrows(RecipeInUseException.class,
				() -> service.delete(RECIPE_ID, VERSION));

		assertEquals(offers, error.getOfferIds());
		assertFalse(recipe.isDeleted());
		assertEquals(VERSION, recipe.getVersion());
		verify(recipeRepository, never()).saveAndFlush(any());
		verifyNoInteractions(revisionRepository, inventoryClient);
	}

	@Test
	void rejectsAStaleVersionBeforeCheckingReferences() {
		Recipe recipe = existing();

		assertThrows(StaleRecipeException.class, () -> service.delete(RECIPE_ID, VERSION - 1));

		assertFalse(recipe.isDeleted());
		verify(recipeRepository, never()).saveAndFlush(any());
		verifyNoInteractions(offerRepository, revisionRepository, inventoryClient);
	}

	@Test
	void rejectsAMissingOrAlreadyDeletedRecipe() {
		when(recipeRepository.findForDeletionById(RECIPE_ID)).thenReturn(Optional.empty());

		assertThrows(RecipeNotFoundException.class, () -> service.delete(RECIPE_ID, VERSION));

		verify(recipeRepository, never()).saveAndFlush(any());
		verifyNoInteractions(offerRepository, revisionRepository, inventoryClient);
	}

	@Test
	void aDeletedRecipeCannotBeReadOrEditedAsCurrent() {
		Recipe recipe = existing();
		recipe.delete();
		when(recipeRepository.findById(RECIPE_ID)).thenReturn(Optional.of(recipe));

		assertThrows(RecipeNotFoundException.class, () -> service.get(RECIPE_ID));
		assertThrows(RecipeNotFoundException.class,
				() -> service.update(RECIPE_ID, VERSION, "Updated", null, null, null));

		verify(recipeRepository, never()).saveAndFlush(any());
		verifyNoInteractions(revisionRepository, inventoryClient);
	}

	private Recipe existing() {
		Recipe recipe = new Recipe();
		ReflectionTestUtils.setField(recipe, "id", RECIPE_ID);
		ReflectionTestUtils.setField(recipe, "version", VERSION);
		when(recipeRepository.findForDeletionById(RECIPE_ID)).thenReturn(Optional.of(recipe));
		return recipe;
	}

}
