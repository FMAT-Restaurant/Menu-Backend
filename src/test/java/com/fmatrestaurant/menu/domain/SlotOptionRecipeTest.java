package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SlotOptionRecipeTest {

	private static final UUID RECIPE = UUID.randomUUID();

	@Test
	void aRecipeOptionKeepsTheRecipeAndTheRevisionAndHasNoInventoryContent() {
		SlotOption option = new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 2);

		assertEquals(SourceType.RECIPE, option.getSourceType());
		assertEquals(RECIPE, option.getRecipeId());
		assertEquals(2, option.getRecipeRevision());
		assertEquals("House burger", option.getDisplayName());
		assertEquals(OfferStatus.ACTIVE, option.getStatus());
		assertNull(option.getInventoryItemId());
		assertNull(option.getSourceName());
		assertNull(option.getQuantity());
		assertNull(option.getUnit());
	}

	@Test
	void rejectsAMissingStatusDisplayNameRecipeOrRevision() {
		assertEquals("/status", assertThrows(InvalidFieldException.class,
				() -> new SlotOption("House burger", null, RECIPE, 1)).getPath());
		assertEquals("/displayName", assertThrows(InvalidFieldException.class,
				() -> new SlotOption(" ", OfferStatus.ACTIVE, RECIPE, 1)).getPath());
		assertEquals("/recipeId", assertThrows(InvalidFieldException.class,
				() -> new SlotOption("House burger", OfferStatus.ACTIVE, null, 1)).getPath());
		assertEquals("/recipeRevision", assertThrows(InvalidFieldException.class,
				() -> new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, null)).getPath());
		assertEquals("/recipeRevision", assertThrows(InvalidFieldException.class,
				() -> new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 0)).getPath());
	}

	@Test
	void updatingWithoutValuesKeepsTheOption() {
		SlotOption option = new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 1);

		option.updateRecipe(null, null, null, null);

		assertEquals("House burger", option.getDisplayName());
		assertEquals(OfferStatus.ACTIVE, option.getStatus());
		assertEquals(RECIPE, option.getRecipeId());
		assertEquals(1, option.getRecipeRevision());
	}

	@Test
	void updatingChangesTheNameAndTheStatusWithoutChangingTheRecipe() {
		SlotOption option = new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 1);

		option.updateRecipe("Burger", OfferStatus.INACTIVE, null, null);

		assertEquals("Burger", option.getDisplayName());
		assertEquals(OfferStatus.INACTIVE, option.getStatus());
		assertEquals(1, option.getRecipeRevision());
	}

	@Test
	void reassigningChangesTheRecipeAndTheRevisionTogether() {
		SlotOption option = new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 1);
		UUID other = UUID.randomUUID();

		option.updateRecipe(null, null, other, 4);

		assertEquals(other, option.getRecipeId());
		assertEquals(4, option.getRecipeRevision());
	}

	@Test
	void theRecipeAndTheRevisionCannotBeChangedSeparately() {
		SlotOption option = new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 1);

		assertEquals("/recipeId", assertThrows(InvalidFieldException.class,
				() -> option.updateRecipe(null, null, null, 2)).getPath());
		assertEquals("/recipeRevision", assertThrows(InvalidFieldException.class,
				() -> option.updateRecipe(null, null, UUID.randomUUID(), null)).getPath());
		assertEquals(RECIPE, option.getRecipeId());
		assertEquals(1, option.getRecipeRevision());
	}

	@Test
	void anInvalidUpdateLeavesTheOptionUnchanged() {
		SlotOption option = new SlotOption("House burger", OfferStatus.ACTIVE, RECIPE, 1);

		assertThrows(InvalidFieldException.class,
				() -> option.updateRecipe("Burger", OfferStatus.INACTIVE, RECIPE, 0));
		assertThrows(InvalidFieldException.class, () -> option.updateRecipe(" ", null, null, null));

		assertEquals("House burger", option.getDisplayName());
		assertEquals(OfferStatus.ACTIVE, option.getStatus());
		assertEquals(1, option.getRecipeRevision());
	}

}
