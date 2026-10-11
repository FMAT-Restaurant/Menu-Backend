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
 * content source (BR-MENU-017); for now only direct Inventory content, with a positive quantity
 * and a unit compatible with the article (BR-MENU-018, INV-MENU-005).
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

	@Column(name = "inventory_item_id", nullable = false)
	private UUID inventoryItemId;

	/** Name of the article when it was referenced, so reads do not depend on Inventory. */
	@Column(name = "source_name", nullable = false, length = SOURCE_NAME_MAX_LENGTH)
	private String sourceName;

	// ponytail: OPEN-006 leaves range and precision open, so the value is kept as sent.
	@Column(nullable = false, columnDefinition = "numeric")
	private BigDecimal quantity;

	@Column(nullable = false, length = UNIT_MAX_LENGTH)
	private String unit;

	protected SlotOption() {
		// Required by JPA.
	}

	/**
	 * Creates an option whose content is the Inventory item data received by Menu.
	 *
	 * @throws InvalidFieldException if a value is missing or invalid, or the unit is not the one of the article
	 */
	public SlotOption(String displayName, OfferStatus status, InventoryItem item, BigDecimal quantity, String unit) {
		if (status == null) {
			throw new InvalidFieldException("/status", "The option status is required");
		}
		if (item == null) {
			throw new InvalidFieldException("/source", "The inventory item data is required");
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
	 * Applies the given changes; a {@code null} value keeps the current one. A new article includes
	 * its received data so Menu can keep the source snapshot and check unit compatibility locally.
	 * If any value is invalid, nothing is modified.
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
			throw new InvalidFieldException("/source/id", "The inventory item id is required");
		}
		return item.id();
	}

	private static String itemName(InventoryItem item) {
		return Fields.text("/source/name", "inventory item name", item.name(), SOURCE_NAME_MAX_LENGTH);
	}

	private static String itemUnit(InventoryItem item) {
		return Fields.text("/source/unit", "inventory item unit", item.unit(), UNIT_MAX_LENGTH);
	}

	/** OPEN-006 defines no conversions, so the option unit must match the received item's unit. */
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

}
