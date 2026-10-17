package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RecipeRevisionTest {

	private final Recipe recipe = new Recipe();

	private static List<ComponentIngredient> ingredients() {
		UUID item = UUID.randomUUID();
		return List.of(new ComponentIngredient(new InventoryItem(item, "Beef patty", "unit"), BigDecimal.TWO));
	}

	private RecipeRevision revision(String name, String description, String instructions,
			List<ComponentIngredient> ingredients) {
		return new RecipeRevision(recipe, 1, name, description, instructions, ingredients);
	}

	@Test
	void keepsTheTextsTheIngredientsAndTheRevision() {
		RecipeRevision revision = new RecipeRevision(recipe, 3, "Burger", "Classic", "Grill and serve", ingredients());

		assertEquals(recipe, revision.getRecipe());
		assertEquals(3, revision.getRevision());
		assertEquals("Burger", revision.getName());
		assertEquals("Classic", revision.getDescription());
		assertEquals("Grill and serve", revision.getInstructions());
		assertEquals(1, revision.getIngredients().size());
	}

	@Test
	void stripsTheTexts() {
		RecipeRevision revision = revision(" Burger ", " Classic ", " Grill ", ingredients());

		assertEquals("Burger", revision.getName());
		assertEquals("Classic", revision.getDescription());
		assertEquals("Grill", revision.getInstructions());
	}

	@Test
	void rejectsAMissingOrBlankText() {
		assertEquals("/name", assertThrows(InvalidFieldException.class,
				() -> revision(" ", "Classic", "Grill", ingredients())).getPath());
		assertEquals("/description", assertThrows(InvalidFieldException.class,
				() -> revision("Burger", null, "Grill", ingredients())).getPath());
		assertEquals("/instructions", assertThrows(InvalidFieldException.class,
				() -> revision("Burger", "Classic", "", ingredients())).getPath());
	}

	@Test
	void rejectsATooLongNameOrDescription() {
		String name = "n".repeat(RecipeRevision.NAME_MAX_LENGTH + 1);
		String description = "d".repeat(RecipeRevision.DESCRIPTION_MAX_LENGTH + 1);

		assertEquals("/name", assertThrows(InvalidFieldException.class,
				() -> revision(name, "Classic", "Grill", ingredients())).getPath());
		assertEquals("/description", assertThrows(InvalidFieldException.class,
				() -> revision("Burger", description, "Grill", ingredients())).getPath());
	}

	@Test
	void acceptsLongInstructions() {
		String instructions = "step ".repeat(5000);

		RecipeRevision revision = revision("Burger", "Classic", instructions, ingredients());

		assertEquals(instructions.strip(), revision.getInstructions());
	}

	@Test
	void rejectsARecipeWithoutIngredients() {
		assertEquals("/supplies", assertThrows(InvalidFieldException.class,
				() -> revision("Burger", "Classic", "Grill", null)).getPath());
		assertEquals("/supplies", assertThrows(InvalidFieldException.class,
				() -> revision("Burger", "Classic", "Grill", List.of())).getPath());
	}

	@Test
	void theIngredientsCannotBeChangedFromOutside() {
		RecipeRevision revision = revision("Burger", "Classic", "Grill", ingredients());
		List<ComponentIngredient> exposed = revision.getIngredients();
		ComponentIngredient other = ingredients().getFirst();

		assertThrows(UnsupportedOperationException.class, () -> exposed.add(other));
	}

}
