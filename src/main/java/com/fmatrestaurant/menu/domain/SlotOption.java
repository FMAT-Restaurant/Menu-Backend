package com.fmatrestaurant.menu.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Contextual appearance of a content inside a slot (BR-MENU-012, BR-MENU-015). It has exactly one
 * content source (BR-MENU-017): direct Inventory content, with a positive quantity and a unit
 * compatible with the article as Inventory returned it (BR-MENU-018, INV-MENU-005), or a recipe of
 * the library, fixed by its identifier and revision (BR-MENU-020).
 *
 * <p>The quantity is the physical amount of the article, not the selection rounds of the slot
 * (BR-MENU-010).
 */
@Entity
@Table(name = "slot_option")
public class SlotOption {

	/** Maximum length of the display name, matching the database column. */
	public static final int DISPLAY_NAME_MAX_LENGTH = 255;

	/** Maximum length of the unit, matching the database column. */
	public static final int UNIT_MAX_LENGTH = 32;

	/** Maximum length of the source name, matching the database column. */
	public static final int SOURCE_NAME_MAX_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX_LENGTH)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private OfferStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 32)
	private SourceType sourceType;

	@Column(name = "inventory_item_id")
	private UUID inventoryItemId;

	/** Name of the article as Inventory returned it when it was referenced. */
	@Column(name = "source_name", length = SOURCE_NAME_MAX_LENGTH)
	private String sourceName;

	// ponytail: OPEN-006 leaves range and precision open, so the value is kept as sent.
	@Column(columnDefinition = "numeric")
	private BigDecimal quantity;

	@Column(length = UNIT_MAX_LENGTH)
	private String unit;

	/** Only for recipe content: the recipe of the library and the revision this option is fixed to. */
	@Column(name = "recipe_id")
	private UUID recipeId;

	@Column(name = "recipe_revision")
	private Integer recipeRevision;

	protected SlotOption() {
		// Required by JPA.
	}

	/**
	 * Creates an option whose content is the Inventory article, as Inventory returned it.
	 *
	 * @throws InvalidFieldException if a value is missing or invalid, or the unit is not the one of the article
	 */
	public SlotOption(String displayName, OfferStatus status, InventoryItem item, BigDecimal quantity, String unit) {
		if (status == null) {
			throw new InvalidFieldException("/status", "The option status is required");
		}
		if (item == null) {
			throw new InvalidFieldException("/inventoryItemId", "The inventory item is required");
		}
		this.displayName = Fields.text("/displayName", "option display name", displayName, DISPLAY_NAME_MAX_LENGTH);
		this.quantity = quantity(quantity);
		this.inventoryItemId = itemId(item);
		this.sourceName = itemName(item);
		this.unit = unit(unit, itemUnit(item));
		this.status = status;
		this.sourceType = SourceType.INVENTORY_ITEM;
	}

	/**
	 * Applies the given changes; a {@code null} value keeps the current one. When the article or the
	 * unit change, {@code item} is the article as Inventory returned it, so the unit is checked against
	 * its current unit. If any value is invalid, nothing is modified.
	 *
	 * @throws InvalidFieldException if a value is invalid or the unit is not the one of the article
	 */
	public void update(String displayName, OfferStatus status, InventoryItem item, BigDecimal quantity, String unit) {
		String newDisplayName = displayName == null ? this.displayName
				: Fields.text("/displayName", "option display name", displayName, DISPLAY_NAME_MAX_LENGTH);
		BigDecimal newQuantity = quantity == null ? this.quantity : quantity(quantity);
		UUID newInventoryItemId = item == null ? this.inventoryItemId : itemId(item);
		String newSourceName = item == null ? this.sourceName : itemName(item);
		String sourceUnit = item == null ? this.unit : itemUnit(item);
		String newUnit = this.unit;
		if (item != null || unit != null) {
			String unitToValidate = this.unit;
			if (unit != null) {
				unitToValidate = unit;
			}
			newUnit = unit(unitToValidate, sourceUnit);
		}
		this.displayName = newDisplayName;
		this.quantity = newQuantity;
		this.unit = newUnit;
		if (status != null) {
			this.status = status;
		}
		this.inventoryItemId = newInventoryItemId;
		this.sourceName = newSourceName;
	}

	/**
	 * Creates an option whose content is a recipe of the library. That the revision exists is checked
	 * by the caller, which has access to the library.
	 *
	 * @throws InvalidFieldException if a value is missing or invalid
	 */
	public SlotOption(String displayName, OfferStatus status, UUID recipeId, Integer recipeRevision) {
		if (status == null) {
			throw new InvalidFieldException("/status", "The option status is required");
		}
		this.displayName = Fields.text("/displayName", "option display name", displayName, DISPLAY_NAME_MAX_LENGTH);
		this.recipeId = recipeId(recipeId);
		this.recipeRevision = recipeRevision(recipeRevision);
		this.status = status;
		this.sourceType = SourceType.RECIPE;
	}

	/**
	 * Applies the given changes to a recipe option; a {@code null} value keeps the current one. The
	 * recipe and its revision are always changed together. If any value is invalid, nothing is
	 * modified.
	 *
	 * @throws InvalidFieldException if a value is invalid, or only one of the recipe and the revision is sent
	 */
	public void updateRecipe(String displayName, OfferStatus status, UUID recipeId, Integer recipeRevision) {
		String newDisplayName = displayName == null ? this.displayName
				: Fields.text("/displayName", "option display name", displayName, DISPLAY_NAME_MAX_LENGTH);
		UUID newRecipeId = this.recipeId;
		Integer newRecipeRevision = this.recipeRevision;
		if (recipeId != null || recipeRevision != null) {
			newRecipeId = recipeId(recipeId);
			newRecipeRevision = recipeRevision(recipeRevision);
		}
		this.displayName = newDisplayName;
		this.recipeId = newRecipeId;
		this.recipeRevision = newRecipeRevision;
		if (status != null) {
			this.status = status;
		}
	}

	private static UUID recipeId(UUID recipeId) {
		if (recipeId == null) {
			throw new InvalidFieldException("/recipeId", "The recipe id is required");
		}
		return recipeId;
	}

	private static Integer recipeRevision(Integer recipeRevision) {
		if (recipeRevision == null) {
			throw new InvalidFieldException("/recipeRevision", "The recipe revision is required");
		}
		if (recipeRevision < 1) {
			throw new InvalidFieldException("/recipeRevision", "The recipe revision must be 1 or greater");
		}
		return recipeRevision;
	}

	private static BigDecimal quantity(BigDecimal quantity) {
		if (quantity == null) {
			throw new InvalidFieldException("/quantity", "The inventory item quantity is required");
		}
		if (quantity.signum() <= 0) {
			throw new InvalidFieldException("/quantity", "The inventory item quantity must be positive");
		}
		return quantity;
	}

	private static UUID itemId(InventoryItem item) {
		if (item.id() == null) {
			throw new InvalidFieldException("/inventoryItemId", "The inventory item id is required");
		}
		return item.id();
	}

	private static String itemName(InventoryItem item) {
		return Fields.text("/inventoryItemId", "inventory item name", item.name(), SOURCE_NAME_MAX_LENGTH);
	}

	private static String itemUnit(InventoryItem item) {
		return Fields.text("/inventoryItemId", "inventory item unit", item.unit(), UNIT_MAX_LENGTH);
	}

	/** OPEN-006 defines no conversions, so the option unit must match the unit of the article. */
	private static String unit(String unit, String itemUnit) {
		String value = Fields.text("/unit", "unit", unit, UNIT_MAX_LENGTH);
		if (!value.equals(itemUnit)) {
			throw new InvalidFieldException("/unit",
					"The unit " + value + " is not compatible with the unit " + itemUnit + " of the inventory item");
		}
		return value;
	}

	public UUID getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public OfferStatus getStatus() {
		return status;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public UUID getInventoryItemId() {
		return inventoryItemId;
	}

	public String getSourceName() {
		return sourceName;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public String getUnit() {
		return unit;
	}

	public UUID getRecipeId() {
		return recipeId;
	}

	public Integer getRecipeRevision() {
		return recipeRevision;
	}

}
