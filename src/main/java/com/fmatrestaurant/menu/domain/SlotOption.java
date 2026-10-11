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
	@Column(name = "source_name", nullable = false)
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
	 * Creates an option whose content is an Inventory article.
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
		this.unit = unit(unit, item);
		this.status = status;
		this.sourceType = SourceType.INVENTORY_ITEM;
		this.inventoryItemId = item.id();
		this.sourceName = item.name();
	}

	/**
	 * Applies the given changes; a {@code null} value keeps the current one. A new article or unit
	 * must come with the article, current or new, to check the unit against it. If any value is
	 * invalid, nothing is modified.
	 *
	 * @throws InvalidFieldException if a value is invalid or the unit is not the one of the article
	 */
	public void update(String displayName, OfferStatus status, InventoryItem item, BigDecimal quantity, String unit) {
		String newDisplayName = displayName == null ? this.displayName
				: Fields.text("/displayName", "option display name", displayName, DISPLAY_NAME_MAX_LENGTH);
		BigDecimal newQuantity = quantity == null ? this.quantity : quantity(quantity);
		String newUnit = this.unit;
		if (item != null) {
			newUnit = unit(unit == null ? this.unit : unit, item);
		} else if (unit != null) {
			throw new IllegalArgumentException("Changing the unit requires the inventory item");
		}
		this.displayName = newDisplayName;
		this.quantity = newQuantity;
		this.unit = newUnit;
		if (status != null) {
			this.status = status;
		}
		if (item != null) {
			this.inventoryItemId = item.id();
			this.sourceName = item.name();
		}
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

	/** ponytail: OPEN-006 does not define conversions, so only the unit of the article is compatible. */
	private static String unit(String unit, InventoryItem item) {
		String value = Fields.text("/unit", "unit", unit, UNIT_MAX_LENGTH);
		if (!value.equals(item.unit())) {
			throw new InvalidFieldException("/unit",
					"The unit " + value + " is not compatible with the unit " + item.unit() + " of the inventory item");
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
