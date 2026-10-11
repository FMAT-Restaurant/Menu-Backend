package com.fmatrestaurant.menu.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Sellable presentation of a catalog entry (BR-MENU-005). It has its own optional presentation
 * tag, base price (BR-MENU-007), image and composition of one or more slots (BR-MENU-006).
 *
 * <p>Its status is derived: ACTIVE only while an administrator enabled it and at least one slot is
 * ACTIVE. Losing the last ACTIVE slot makes it INACTIVE and getting one back reactivates it, unless
 * an administrator inactivated it (BR-MENU-008, BR-MENU-016, INV-MENU-002). It never changes the
 * status of the entry.
 */
@Entity
@Table(name = "catalog_offer")
public class CatalogOffer {

	/** Maximum length of the presentation tag, matching the database column. */
	public static final int PRESENTATION_TAG_MAX_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	private Long version;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "entry_id", nullable = false)
	private CatalogEntry entry;

	@Column(name = "presentation_tag", length = PRESENTATION_TAG_MAX_LENGTH)
	private String presentationTag;

	// ponytail: OPEN-001 leaves currency, precision and rounding open, so the price is kept as sent.
	@Column(name = "base_price", nullable = false, columnDefinition = "numeric")
	private BigDecimal basePrice;

	@Column(name = "image_id", nullable = false)
	private UUID imageId;

	/** Administrative intent; the status also needs an ACTIVE slot. */
	@Column(nullable = false)
	private boolean enabled;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "offer_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CompositionSlot> slots = new ArrayList<>();

	protected CatalogOffer() {
		// Required by JPA.
	}

	/**
	 * Creates an offer of the entry. Every new offer starts {@link OfferStatus#INACTIVE}.
	 *
	 * @param presentationTag optional; blank means no tag
	 * @throws InvalidFieldException if a value is missing or invalid, or there are no slots
	 */
	public CatalogOffer(CatalogEntry entry, String presentationTag, BigDecimal basePrice, UUID imageId,
			List<CompositionSlot> slots) {
		if (entry == null) {
			throw new InvalidFieldException("/entryId", "An offer must belong to a catalog entry");
		}
		if (basePrice == null) {
			throw new InvalidFieldException("/basePrice", "The base price is required");
		}
		if (imageId == null) {
			throw new InvalidFieldException("/imageId", "The offer image is required");
		}
		this.entry = entry;
		this.presentationTag = presentationTag(presentationTag);
		this.basePrice = basePrice(basePrice);
		this.imageId = imageId;
		replaceSlots(slots);
	}

	/**
	 * Applies the given changes; a {@code null} value keeps the current one and a blank tag removes
	 * it. If any value is invalid, nothing is modified.
	 *
	 * @throws InvalidFieldException if a value is invalid
	 */
	public void update(String presentationTag, BigDecimal basePrice, UUID imageId) {
		String newTag = presentationTag == null ? this.presentationTag : presentationTag(presentationTag);
		BigDecimal newPrice = basePrice == null ? this.basePrice : basePrice(basePrice);
		this.presentationTag = newTag;
		this.basePrice = newPrice;
		if (imageId != null) {
			this.imageId = imageId;
		}
	}

	/**
	 * Replaces the composition; slots that are not given are removed. The base price does not change.
	 *
	 * @throws InvalidFieldException if there are no slots
	 */
	public void replaceSlots(List<CompositionSlot> newSlots) {
		if (newSlots == null || newSlots.isEmpty()) {
			throw new InvalidFieldException("/composition/slots", "The composition must have at least one slot");
		}
		// Replacing an equal list would still mark the collection dirty and bump the version.
		if (!slots.equals(newSlots)) {
			slots.clear();
			slots.addAll(newSlots);
		}
	}

	/**
	 * Administrative activation or inactivation (REQ-MENU-OFFER-004). An explicit inactivation
	 * prevails over the automatic reactivation until a new activation.
	 *
	 * @throws InvalidFieldException if it is activated without an ACTIVE slot
	 */
	public void changeStatus(OfferStatus status) {
		if (status == OfferStatus.ACTIVE && !hasActiveSlot()) {
			throw new InvalidFieldException("/status", "An offer needs at least one ACTIVE slot to be activated");
		}
		this.enabled = status == OfferStatus.ACTIVE;
	}

	public OfferStatus getStatus() {
		return enabled && hasActiveSlot() ? OfferStatus.ACTIVE : OfferStatus.INACTIVE;
	}

	private boolean hasActiveSlot() {
		return slots.stream().anyMatch(slot -> slot.getStatus() == OfferStatus.ACTIVE);
	}

	private static String presentationTag(String tag) {
		if (tag == null || tag.isBlank()) {
			return null;
		}
		return Fields.text("/presentationTag", "presentation tag", tag, PRESENTATION_TAG_MAX_LENGTH);
	}

	private static BigDecimal basePrice(BigDecimal basePrice) {
		if (basePrice.signum() < 0) {
			throw new InvalidFieldException("/basePrice", "The base price must not be negative");
		}
		return basePrice;
	}

	public UUID getId() {
		return id;
	}

	public Long getVersion() {
		return version;
	}

	public CatalogEntry getEntry() {
		return entry;
	}

	public String getPresentationTag() {
		return presentationTag;
	}

	public BigDecimal getBasePrice() {
		return basePrice;
	}

	public UUID getImageId() {
		return imageId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<CompositionSlot> getSlots() {
		return Collections.unmodifiableList(slots);
	}

}
