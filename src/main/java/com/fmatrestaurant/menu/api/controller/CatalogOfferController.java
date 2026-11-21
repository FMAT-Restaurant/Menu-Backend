package com.fmatrestaurant.menu.api.controller;

import com.fmatrestaurant.menu.api.MenuApi;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fmatrestaurant.menu.api.dto.DataResponse;
import com.fmatrestaurant.menu.api.dto.PageMeta;
import com.fmatrestaurant.menu.api.Revisions;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fmatrestaurant.menu.api.controller.ImageController.ImageResponse;
import com.fmatrestaurant.menu.application.service.CatalogOfferService;
import com.fmatrestaurant.menu.application.service.CatalogOfferService.OfferInput;
import com.fmatrestaurant.menu.application.service.DerivedAvailability;
import com.fmatrestaurant.menu.application.service.DerivedAvailability.OfferAvailability;
import com.fmatrestaurant.menu.domain.model.CatalogOffer;
import com.fmatrestaurant.menu.domain.model.CompositionSlot;
import com.fmatrestaurant.menu.domain.model.OfferStatus;
import com.fmatrestaurant.menu.domain.model.SlotOption;
import com.fmatrestaurant.menu.domain.model.SourceType;

/**
 * Administration of offers and their compositions; slots and options are managed inside the
 * composition. Business rules live in {@link CatalogOfferService} and {@link CatalogOffer}.
 */
@MenuApi("/menu")
public class CatalogOfferController {

	private final CatalogOfferService offerService;

	private final DerivedAvailability availability;

	public CatalogOfferController(CatalogOfferService offerService, DerivedAvailability availability) {
		this.offerService = offerService;
		this.availability = availability;
	}

	/** The initial status is always INACTIVE, its options start ACTIVE by default and the server assigns every id. */
	@PostMapping("/entries/{entryId}/offers")
	public ResponseEntity<DataResponse<OfferDetail>> create(@PathVariable UUID entryId,
                                                            @RequestBody OfferInput request) {
		CatalogOffer offer = offerService.create(entryId, request);
		OfferAvailability available = availability.offer(offer);
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(offer, available))
				.body(new DataResponse<>(OfferDetail.of(offer, available)));
	}

	@GetMapping("/offers")
	public OfferListResponse list(@RequestParam(required = false) UUID entryId,
			@RequestParam(required = false) UUID excludeOfferId,
			@RequestParam(defaultValue = "false") boolean excludeArchivedEntries,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		Page<CatalogOffer> offers = offerService.list(entryId, excludeOfferId, excludeArchivedEntries, page, pageSize);
		Map<UUID, OfferAvailability> available = availability.offers(offers.getContent());
		return new OfferListResponse(offers.getContent().stream()
				.map(offer -> OfferSummary.of(offer, available.get(offer.getId()))).toList(),
				new PageMeta(page, pageSize, offers.getTotalElements(), offers.getTotalPages()));
	}

	/** Spring answers 304 when If-None-Match matches the ETag of the response. */
	@GetMapping("/offers/{offerId}")
	public ResponseEntity<DataResponse<OfferDetail>> get(@PathVariable UUID offerId) {
		return detail(offerService.get(offerId));
	}

	@PatchMapping("/offers/{offerId}")
	public ResponseEntity<DataResponse<OfferDetail>> update(@PathVariable UUID offerId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestBody OfferInput request) {
		return detail(offerService.update(offerId, Revisions.strongVersion(ifMatch), request));
	}

	/** Deletes an INACTIVE offer; when the preconditions are not met, it answers 409. */
	@DeleteMapping("/offers/{offerId}")
	public ResponseEntity<Void> delete(@PathVariable UUID offerId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
		offerService.delete(offerId, Revisions.strongVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<DataResponse<OfferDetail>> detail(CatalogOffer offer) {
		OfferAvailability available = availability.offer(offer);
		return ResponseEntity.ok().eTag(etag(offer, available))
				.body(new DataResponse<>(OfferDetail.of(offer, available)));
	}

	/**
	 * Strong, as the contract requires for If-Match; it changes with every change of the composition and of the
	 * derived availability of the offer, its slots and its options.
	 */
	private static String etag(CatalogOffer offer, OfferAvailability available) {
		return Revisions.strong(offer.getVersion(), available.fingerprint());
	}

	public record OfferSummary(UUID id, UUID entryId, String entryName,
			String presentationTag, BigDecimal basePrice, OfferStatus status,
			ImageResponse image, int slotCount, @JsonProperty("isAvailable") boolean isAvailable) {

		static OfferSummary of(CatalogOffer offer, OfferAvailability available) {
			return new OfferSummary(offer.getId(), offer.getEntry().getId(), offer.getEntry().getBrandName(),
					offer.getPresentationTag(), offer.getBasePrice(), offer.getStatus(),
					ImageResponse.of(offer.getImageId()), offer.getSlots().size(), available.available());
		}

	}

	public record OfferDetail(UUID id, UUID entryId, String presentationTag,
			BigDecimal basePrice, OfferStatus status, ImageResponse image,
			@JsonProperty("isAvailable") boolean isAvailable, Composition composition) {

		static OfferDetail of(CatalogOffer offer, OfferAvailability available) {
			return new OfferDetail(offer.getId(), offer.getEntry().getId(), offer.getPresentationTag(),
					offer.getBasePrice(), offer.getStatus(), ImageResponse.of(offer.getImageId()), available.available(),
					new Composition(offer.getSlots().stream().map(slot -> Slot.of(slot, available)).toList()));
		}

	}

	public record Composition(List<Slot> slots) {
	}

	/**
	 * A slot has no status of its own in the contract. A missing course is left out, as the contract types
	 * it as a string.
	 */
	public record Slot(UUID slotId, String name, int quantity,
			@JsonInclude(NON_NULL) String course, @JsonProperty("isAvailable") boolean isAvailable,
			List<Option> options) {

		static Slot of(CompositionSlot slot, OfferAvailability available) {
			return new Slot(slot.getId(), slot.getName(), slot.getQuantity(), slot.getCourse(),
					available.slot(slot.getId()),
					slot.getOptions().stream().map(option -> Option.of(option, available)).toList());
		}

	}

	/**
	 * An INVENTORY_ITEM option ({@code additionalProperties: false}): only the fields of its source type.
	 * The status is always included.
	 */
	public record Option(UUID optionId, SourceType sourceType, @JsonInclude(NON_NULL) String inventoryItemId,
			@JsonInclude(NON_NULL) BigDecimal quantity, @JsonInclude(NON_NULL) String unit,
			@JsonInclude(NON_NULL) UUID recipeId, OfferStatus status, String displayName,
			@JsonProperty("isAvailable") boolean isAvailable) {

		static Option of(SlotOption option, OfferAvailability available) {
			return new Option(option.getId(), option.getSourceType(), option.getInventoryItemId(),
					option.getQuantity(), option.getUnit(), option.getRecipeId(), option.getStatus(),
					option.getDisplayName(), available.option(option.getId()));
		}

	}

	public record OfferListResponse(List<OfferSummary> data, PageMeta meta) {
	}

}
