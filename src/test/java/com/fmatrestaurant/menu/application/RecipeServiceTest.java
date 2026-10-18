package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.application.RecipeService.SupplyData;
import com.fmatrestaurant.menu.domain.ComponentIngredient;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.Recipe;
import com.fmatrestaurant.menu.domain.RecipeRevision;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;

class RecipeServiceTest {

	private static final UUID RECIPE_ID = UUID.randomUUID();

	private static final String BEEF = "INV-beef-001";

	private static final String BUN = "INV-bun-001";

	private static final long VERSION = 3;

	private RecipeRepository recipeRepository;

	private RecipeRevisionRepository revisionRepository;

	private InventoryClient inventoryClient;

	private RecipeService service;

	@BeforeEach
	void setUp() {
		recipeRepository = mock(RecipeRepository.class);
		revisionRepository = mock(RecipeRevisionRepository.class);
		inventoryClient = mock(InventoryClient.class);
		service = new RecipeService(recipeRepository, revisionRepository, inventoryClient,
				mock(CatalogOfferRepository.class));
		when(inventoryClient.find(BEEF)).thenReturn(Optional.of(inventoryItem(BEEF)));
		when(inventoryClient.find(BUN)).thenReturn(Optional.of(inventoryItem(BUN)));
		when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(recipeRepository.saveAndFlush(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(revisionRepository.saveAndFlush(any(RecipeRevision.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createsTheRecipeAtRevisionOne() {
		RecipeRevision created = service.create("Burger", "Classic", "Grill and serve", List.of(supply(BEEF, "2")));

		assertEquals(1, created.getRevision());
		assertEquals("Burger", created.getName());
		assertEquals("Classic", created.getDescription());
		assertEquals("Grill and serve", created.getInstructions());
		assertEquals(1, created.getIngredients().size());
		assertEquals(1, created.getRecipe().getCurrentRevision());
		verify(recipeRepository).save(created.getRecipe());
		verify(revisionRepository).saveAndFlush(created);
	}

	@Test
	void createRejectsMissingIngredientsWithoutSaving() {
		InvalidFieldException none = assertThrows(InvalidFieldException.class,
				() -> service.create("Burger", "Classic", "Grill", null));
		InvalidFieldException empty = assertThrows(InvalidFieldException.class,
				() -> service.create("Burger", "Classic", "Grill", List.of()));

		assertEquals("/supplies", none.getPath());
		assertEquals("/supplies", empty.getPath());
		verify(recipeRepository, never()).save(any(Recipe.class));
	}

	@Test
	void createPrefixesTheViolationsOfAnIngredientWithItsPosition() {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> service.create("Burger", "Classic", "Grill", List.of(supply(BEEF, "2"), supply(BUN, "0"))));

		assertEquals("/supplies/1/quantity", exception.getPath());
		verify(recipeRepository, never()).save(any(Recipe.class));
	}

	@Test
	void createRejectsAMissingIngredient() {
		List<SupplyData> supplies = new ArrayList<>();
		supplies.add(null);

		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> service.create("Burger", "Classic", "Grill", supplies));

		assertEquals("/supplies/0", exception.getPath());
	}

	@Test
	void createRejectsInvalidTextsWithoutSaving() {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> service.create(" ", "Classic", "Grill", List.of(supply(BEEF, "2"))));

		assertEquals("/name", exception.getPath());
		verify(recipeRepository, never()).save(any(Recipe.class));
		verify(revisionRepository, never()).saveAndFlush(any(RecipeRevision.class));
	}

	@Test
	void createRejectsAnArticleThatDoesNotExistInInventory() {
		String unknown = "unknown-item";
		when(inventoryClient.find(unknown)).thenReturn(Optional.empty());

		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> service.create("Burger", "Classic", "Grill", List.of(supply(BEEF, "2"), supply(unknown, "1"))));

		assertEquals("/supplies/1/inventoryItemId", exception.getPath());
		verify(recipeRepository, never()).save(any(Recipe.class));
	}

	@Test
	void createRejectsAMissingArticleId() {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> service.create("Burger", "Classic", "Grill", List.of(new SupplyData(null, BigDecimal.ONE))));

		assertEquals("/supplies/0/inventoryItemId", exception.getPath());
	}

	@Test
	void createSavesNothingWhenInventoryCannotConfirmTheArticles() {
		when(inventoryClient.find(BEEF)).thenThrow(new RestClientException("Inventory is down"));

		assertThrows(RestClientException.class,
				() -> service.create("Burger", "Classic", "Grill", List.of(supply(BEEF, "2"))));

		verify(recipeRepository, never()).save(any(Recipe.class));
		verify(revisionRepository, never()).saveAndFlush(any(RecipeRevision.class));
	}

	@Test
	void inventoryIsConsultedOncePerArticleInEachOperation() {
		RecipeRevision created = service.create("Burger", "Classic", "Grill", List.of(supply(BEEF, "2"), supply(BEEF, "3")));

		assertEquals(2, created.getIngredients().size());
		verify(inventoryClient, times(1)).find(BEEF);
	}

	@Test
	void updateDoesNotConsultInventoryWhenTheIngredientsAreNotSent() {
		existing(1);

		service.update(RECIPE_ID, VERSION, "Better burger", null, null, null);

		verify(inventoryClient, never()).find(any(String.class));
	}

	@Test
	void listChecksThePagination() {
		assertEquals("page", assertThrows(InvalidFieldException.class, () -> service.list(null, 0, 12)).getPath());
		assertEquals("pageSize", assertThrows(InvalidFieldException.class, () -> service.list(null, 1, 0)).getPath());
	}

	@Test
	@SuppressWarnings("unchecked")
	void listAsksForTheRequestedPageOrderedByName() {
		RecipeRevision revision = new RecipeRevision(new Recipe(), 1, "Burger", "Classic", "Grill",
				List.of(ingredient()));
		Page<RecipeRevision> page = new PageImpl<>(List.of(revision));
		when(revisionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		Page<RecipeRevision> result = service.list("burger", 2, 5);

		assertSame(page, result);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(revisionRepository).findAll(any(Specification.class), pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(5, pageable.getValue().getPageSize());
        assertNotNull(pageable.getValue().getSort().getOrderFor("name"));
        assertNotNull(pageable.getValue().getSort().getOrderFor("id"));
	}

	@Test
	void getReturnsTheCurrentRevision() {
		RecipeRevision current = existing(2);

		assertSame(current, service.get(RECIPE_ID));
	}

	@Test
	void getFailsWhenTheRecipeDoesNotExist() {
		when(recipeRepository.findById(RECIPE_ID)).thenReturn(Optional.empty());

		assertThrows(RecipeNotFoundException.class, () -> service.get(RECIPE_ID));
	}

	@Test
	void getRevisionReturnsAnEarlierRevision() {
		RecipeRevision first = new RecipeRevision(new Recipe(), 1, "Old burger", "Classic", "Grill",
				List.of(ingredient()));
		when(revisionRepository.findByRecipeIdAndRevision(RECIPE_ID, 1)).thenReturn(Optional.of(first));

		assertSame(first, service.getRevision(RECIPE_ID, 1));
	}

	@Test
	void getRevisionFailsWhenTheRevisionDoesNotExist() {
		when(revisionRepository.findByRecipeIdAndRevision(RECIPE_ID, 9)).thenReturn(Optional.empty());

		RecipeNotFoundException exception = assertThrows(RecipeNotFoundException.class,
				() -> service.getRevision(RECIPE_ID, 9));

		assertEquals("Recipe " + RECIPE_ID + " has no revision 9", exception.getMessage());
	}

	@Test
	void updateFailsWhenTheRecipeDoesNotExist() {
		when(recipeRepository.findById(RECIPE_ID)).thenReturn(Optional.empty());

		assertThrows(RecipeNotFoundException.class,
				() -> service.update(RECIPE_ID, VERSION, "Burger", null, null, null));
	}

	@Test
	void updateRejectsAStaleVersionWithoutPublishing() {
		existing(1);

		assertThrows(StaleRecipeException.class,
				() -> service.update(RECIPE_ID, VERSION + 1, "Better burger", null, null, null));

		verify(recipeRepository, never()).saveAndFlush(any(Recipe.class));
		verify(revisionRepository, never()).saveAndFlush(any(RecipeRevision.class));
	}

	@Test
	void updateWithoutChangesPublishesNothing() {
		RecipeRevision current = existing(1);

		RecipeRevision result = service.update(RECIPE_ID, VERSION, null, null, null, null);

		assertSame(current, result);
		verify(recipeRepository, never()).saveAndFlush(any(Recipe.class));
		verify(revisionRepository, never()).saveAndFlush(any(RecipeRevision.class));
	}

	@Test
	void updatePublishesTheNextRevisionKeepingWhatWasNotSent() {
		RecipeRevision current = existing(1);

		RecipeRevision next = service.update(RECIPE_ID, VERSION, "Better burger", null, null, null);

		assertEquals(2, next.getRevision());
		assertEquals(2, next.getRecipe().getCurrentRevision());
		assertEquals("Better burger", next.getName());
		assertEquals(current.getDescription(), next.getDescription());
		assertEquals(current.getInstructions(), next.getInstructions());
		assertEquals(1, next.getIngredients().size());
		assertNotSame(current.getIngredients().getFirst(), next.getIngredients().getFirst());
		assertEquals(current.getIngredients().getFirst().getInventoryItemId(),
				next.getIngredients().getFirst().getInventoryItemId());
		assertEquals("Burger", current.getName());
		verify(recipeRepository).saveAndFlush(next.getRecipe());
		verify(revisionRepository).saveAndFlush(next);
	}

	@Test
	void updateReplacesTheIngredientsWhenTheyAreSent() {
		existing(1);

		RecipeRevision next = service.update(RECIPE_ID, VERSION, null, "Updated", "Grill longer",
				List.of(supply(BUN, "1")));

		assertEquals("Updated", next.getDescription());
		assertEquals("Grill longer", next.getInstructions());
		assertEquals(1, next.getIngredients().size());
		assertEquals(BUN, next.getIngredients().getFirst().getInventoryItemId());
	}

	@Test
	void updateRejectsInvalidIngredientsWithoutPublishing() {
		RecipeRevision current = existing(1);

		assertEquals("/supplies", assertThrows(InvalidFieldException.class,
				() -> service.update(RECIPE_ID, VERSION, null, null, null, List.of())).getPath());
		assertEquals("/supplies/0/quantity", assertThrows(InvalidFieldException.class,
				() -> service.update(RECIPE_ID, VERSION, null, null, null, List.of(supply(BUN, "-1")))).getPath());

		assertEquals(1, current.getRecipe().getCurrentRevision());
		verify(recipeRepository, never()).saveAndFlush(any(Recipe.class));
		verify(revisionRepository, never()).saveAndFlush(any(RecipeRevision.class));
	}

	@Test
	void updateRejectsInvalidTextsWithoutPublishing() {
		RecipeRevision current = existing(1);

		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> service.update(RECIPE_ID, VERSION, " ", null, null, null));

		assertEquals("/name", exception.getPath());
		assertEquals(1, current.getRecipe().getCurrentRevision());
		verify(revisionRepository, never()).saveAndFlush(any(RecipeRevision.class));
	}

	/** A recipe as loaded by JPA, at the given revision, with its current revision. */
	private RecipeRevision existing(int currentRevision) {
		Recipe recipe = new Recipe();
		for (int i = 1; i < currentRevision; i++) {
			recipe.publishNextRevision();
		}
		ReflectionTestUtils.setField(recipe, "id", RECIPE_ID);
		ReflectionTestUtils.setField(recipe, "version", VERSION);
		RecipeRevision current = new RecipeRevision(recipe, currentRevision, "Burger", "Classic", "Grill and serve",
				List.of(ingredient()));
		when(recipeRepository.findById(RECIPE_ID)).thenReturn(Optional.of(recipe));
		when(revisionRepository.findByRecipeIdAndRevision(RECIPE_ID, currentRevision)).thenReturn(Optional.of(current));
		return current;
	}

	private static SupplyData supply(String item, String quantity) {
		return new SupplyData(item, new BigDecimal(quantity));
	}

	private static ComponentIngredient ingredient() {
		return new ComponentIngredient(inventoryItem(RecipeServiceTest.BEEF), new BigDecimal("2"));
	}

	private static InventoryItem inventoryItem(String item) {
		return new InventoryItem(item, "Item " + item, "unit");
	}

}
