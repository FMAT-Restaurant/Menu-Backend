package com.fmatrestaurant.menu.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Published history independent of the lifetime of the live entry, offer and composition. */
@Entity
@Immutable
@Table(name = "catalog_offer_revision", uniqueConstraints = @UniqueConstraint(name = "uk_offer_revision",
		columnNames = { "offer_id", "revision" }))
public class CatalogOfferRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "entry_id", nullable = false, updatable = false)
	private UUID entryId;

	@Column(name = "offer_id", nullable = false, updatable = false)
	private UUID offerId;

	@Column(nullable = false, updatable = false)
	private long revision;

	@Column(name = "brand_name_snapshot", nullable = false, updatable = false,
			length = CatalogEntry.BRAND_NAME_MAX_LENGTH)
	private String brandNameSnapshot;

	@Column(name = "presentation_tag", nullable = false, updatable = false,
			length = CatalogOffer.PRESENTATION_TAG_MAX_LENGTH)
	private String presentationTag;

	@Column(name = "base_price", nullable = false, updatable = false, columnDefinition = "numeric")
	private BigDecimal basePrice;

	@Column(name = "image_id", nullable = false, updatable = false)
	private UUID imageId;

	@Convert(converter = CompositionSnapshotConverter.class)
	@Column(name = "composition_snapshot", nullable = false, updatable = false, columnDefinition = "text")
	private CompositionSnapshot compositionSnapshot;

	protected CatalogOfferRevision() {
	}

	public CatalogOfferRevision(CatalogOffer offer, long revision) {
		if (offer.getId() == null || offer.getEntry().getId() == null || revision < 1) {
			throw new IllegalArgumentException("A revision requires a persisted offer and a positive revision number");
		}
		this.entryId = offer.getEntry().getId();
		this.offerId = offer.getId();
		this.revision = revision;
		this.brandNameSnapshot = offer.getEntry().getBrandName();
		this.presentationTag = offer.getPresentationTag();
		this.basePrice = offer.getBasePrice();
		this.imageId = offer.getImageId();
		this.compositionSnapshot = CompositionSnapshot.of(offer);
	}

	public UUID getId() {
		return id;
	}

	public UUID getEntryId() {
		return entryId;
	}

	public UUID getOfferId() {
		return offerId;
	}

	public long getRevision() {
		return revision;
	}

	public String getBrandNameSnapshot() {
		return brandNameSnapshot;
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

	public CompositionSnapshot getCompositionSnapshot() {
		return compositionSnapshot;
	}
}
