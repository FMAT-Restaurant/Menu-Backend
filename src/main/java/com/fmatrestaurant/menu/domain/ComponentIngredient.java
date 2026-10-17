package com.fmatrestaurant.menu.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ingredient line of a recipe revision: an Inventory article and the quantity the recipe uses
 * (BR-MENU-019, INV-MENU-005). Menu keeps the name and the unit of the article as Inventory returned
 * them when the line was written, because Inventory owns the article (BR-MENU-018).
 *
 * <p>The quantity is the physical amount of the article; it is not the yield of the recipe nor the
 * selection rounds of a slot.
 */
@Entity
@Table(name = "component_ingredient")
public class ComponentIngredient {

	/** Maximum length of the item name, matching the database column. */
	public static final int NAME_MAX_LENGTH = 255;

	/** Maximum length of the unit, matching the database column. */
	public static final int UNIT_MAX_LENGTH = 32;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "inventory_item_id", nullable = false)
	private UUID inventoryItemId;

	@Column(name = "item_name", nullable = false, length = NAME_MAX_LENGTH)
	private String itemName;

	// ponytail: OPEN-006 leaves range and precision open, so the value is kept as sent.
	@Column(nullable = false, columnDefinition = "numeric")
	private BigDecimal quantity;

	/** The unit of the article: OPEN-006 defines no conversions. */
	@Column(nullable = false, length = UNIT_MAX_LENGTH)
	private String unit;

	protected ComponentIngredient() {
		// Required by JPA.
	}

	/**
	 * @param item the article as Inventory returned it
	 * @throws InvalidFieldException if a value is missing or invalid
	 */
	public ComponentIngredient(InventoryItem item, BigDecimal quantity) {
		if (item == null || item.id() == null) {
			throw new InvalidFieldException("/inventoryItemId", "The inventory item id is required");
		}
		if (quantity == null) {
			throw new InvalidFieldException("/quantity", "The quantity is required");
		}
		if (quantity.signum() <= 0) {
			throw new InvalidFieldException("/quantity", "The quantity must be positive");
		}
		this.inventoryItemId = item.id();
		this.itemName = Fields.text("/inventoryItemId", "inventory item name", item.name(), NAME_MAX_LENGTH);
		this.unit = Fields.text("/inventoryItemId", "inventory item unit", item.unit(), UNIT_MAX_LENGTH);
		this.quantity = quantity;
	}

	/** A new line with the same values, for the next revision of the recipe. */
	public ComponentIngredient copy() {
		ComponentIngredient copy = new ComponentIngredient();
		copy.inventoryItemId = inventoryItemId;
		copy.itemName = itemName;
		copy.quantity = quantity;
		copy.unit = unit;
		return copy;
	}

	public UUID getId() {
		return id;
	}

	public UUID getInventoryItemId() {
		return inventoryItemId;
	}

	public String getItemName() {
		return itemName;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public String getUnit() {
		return unit;
	}

}
