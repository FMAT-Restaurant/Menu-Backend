package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ComponentIngredientTest {

	private static final String ITEM = "INV-item-001";

	private static final InventoryItem BEEF = new InventoryItem(ITEM, "Beef patty", "unit");

	@Test
	void keepsTheArticleAsInventoryReturnedItAndTheQuantity() {
		ComponentIngredient ingredient = new ComponentIngredient(BEEF, new BigDecimal("2.5"));

		assertEquals(ITEM, ingredient.getInventoryItemId());
		assertEquals("Beef patty", ingredient.getItemName());
		assertEquals("unit", ingredient.getUnit());
		assertEquals(new BigDecimal("2.5"), ingredient.getQuantity());
	}

	@Test
	void stripsTheTexts() {
		ComponentIngredient ingredient = new ComponentIngredient(new InventoryItem(ITEM, " Beef patty ", " g "),
				BigDecimal.ONE);

		assertEquals("Beef patty", ingredient.getItemName());
		assertEquals("g", ingredient.getUnit());
	}

	@Test
	void rejectsAMissingArticleOrArticleId() {
		assertEquals("/inventoryItemId", assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(null, BigDecimal.ONE)).getPath());
		assertEquals("/inventoryItemId", assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(new InventoryItem(null, "Beef patty", "unit"), BigDecimal.ONE)).getPath());
	}

	@Test
	void rejectsAMissingQuantity() {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(BEEF, null));

		assertEquals("/quantity", exception.getPath());
	}

	@Test
	void rejectsAQuantityThatIsNotPositive() {
		assertThrows(InvalidFieldException.class, () -> new ComponentIngredient(BEEF, BigDecimal.ZERO));
		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(BEEF, new BigDecimal("-1")));

		assertEquals("/quantity", exception.getPath());
	}

	@Test
	void rejectsABlankOrTooLongNameAndUnit() {
		String longName = "n".repeat(ComponentIngredient.NAME_MAX_LENGTH + 1);
		String longUnit = "u".repeat(ComponentIngredient.UNIT_MAX_LENGTH + 1);

		assertEquals("/inventoryItemId", assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(new InventoryItem(ITEM, " ", "unit"), BigDecimal.ONE)).getPath());
		assertEquals("/inventoryItemId", assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(new InventoryItem(ITEM, "Beef patty", null), BigDecimal.ONE)).getPath());
		assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(new InventoryItem(ITEM, longName, "unit"), BigDecimal.ONE));
		assertThrows(InvalidFieldException.class,
				() -> new ComponentIngredient(new InventoryItem(ITEM, "Beef patty", longUnit), BigDecimal.ONE));
	}

	@Test
	void aCopyHasTheSameValuesAndIsANewLine() {
		ComponentIngredient ingredient = new ComponentIngredient(BEEF, new BigDecimal("2"));

		ComponentIngredient copy = ingredient.copy();

		assertNotSame(ingredient, copy);
		assertEquals(ingredient.getInventoryItemId(), copy.getInventoryItemId());
		assertEquals(ingredient.getItemName(), copy.getItemName());
		assertEquals(ingredient.getUnit(), copy.getUnit());
		assertEquals(ingredient.getQuantity(), copy.getQuantity());
	}

}
