package com.fmatrestaurant.menu.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmatrestaurant.menu.api.ImageController.ImageResponse;
import com.fmatrestaurant.menu.application.CatalogOfferService;
import com.fmatrestaurant.menu.application.CatalogOfferService.OfferInput;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.CompositionSlot;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.domain.SlotOption;
import com.fmatrestaurant.menu.domain.SourceType;

/**
 * Administration of offers and their compositions; slots and options are managed inside the
 * composition. Business rules live in {@link CatalogOfferService} and {@link CatalogOffer}.
 */
@RestController
@RequestMapping("/api/v1/menu")
public class CatalogOfferController {

	private final CatalogOfferService offerService;

	public CatalogOfferController(CatalogOfferService offerService) {
		this.offerService = offerService;
	}

	/** The initial status is always INACTIVE and the server assigns every id. */
	@PostMapping("/entries/{entryId}/offers")
	public ResponseEntity<DataResponse<OfferDetail>> create(@PathVariable UUID entryId,
			@RequestBody OfferInput request) {
		CatalogOffer offer = offerService.create(entryId, request);
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(offer)).body(new DataResponse<>(OfferDetail.of(offer)));
	}

	@GetMapping("/offers")
	public OfferListResponse list(@RequestParam(required = false) UUID entryId,
			@RequestParam(required = false) UUID excludeOfferId,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		Page<CatalogOffer> offers = offerService.list(entryId, excludeOfferId, page, pageSize);
		return new OfferListResponse(offers.getContent().stream().map(OfferSummary::of).toList(),
				new PageMeta(page, pageSize, offers.getTotalElements(), offers.getTotalPages()));
	}

	/** Spring answers 304 when If-None-Match matches the ETag of the response. */
	@GetMapping("/offers/{offerId}")
	public ResponseEntity<DataResponse<OfferDetail>> get(@PathVariable UUID offerId) {
		CatalogOffer offer = offerService.get(offerId);
		return ResponseEntity.ok().eTag(etag(offer)).body(new DataResponse<>(OfferDetail.of(offer)));
	}

	@PatchMapping("/offers/{offerId}")
	public ResponseEntity<DataResponse<OfferDetail>> update(@PathVariable UUID offerId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestBody OfferInput request) {
		CatalogOffer offer = offerService.update(offerId, Revisions.strongVersion(ifMatch), request);
		return ResponseEntity.ok().eTag(etag(offer)).body(new DataResponse<>(OfferDetail.of(offer)));
	}

	/** Strong, as the contract requires for If-Match; it changes with every change of the composition. */
	private static String etag(CatalogOffer offer) {
		return Revisions.strong(offer.getVersion());
	}

	public record OfferSummary(UUID id, UUID entryId, String entryName,
			@JsonInclude(NON_NULL) String presentationTag, BigDecimal basePrice, OfferStatus status,
			ImageResponse image, int slotCount) {

		static OfferSummary of(CatalogOffer offer) {
			return new OfferSummary(offer.getId(), offer.getEntry().getId(), offer.getEntry().getBrandName(),
					offer.getPresentationTag(), offer.getBasePrice(), offer.getStatus(),
					ImageResponse.of(offer.getImageId()), offer.getSlots().size());
		}

	}

	public record OfferDetail(UUID id, UUID entryId, @JsonInclude(NON_NULL) String presentationTag,
			BigDecimal basePrice, OfferStatus status, ImageResponse image, Composition composition) {

		static OfferDetail of(CatalogOffer offer) {
			return new OfferDetail(offer.getId(), offer.getEntry().getId(), offer.getPresentationTag(),
					offer.getBasePrice(), offer.getStatus(), ImageResponse.of(offer.getImageId()),
					new Composition(offer.getSlots().stream().map(Slot::of).toList()));
		}

	}

	public record Composition(List<Slot> slots) {
	}

	/**
	 * Every slot takes part in the selection, so {@code required} is always {@code true}. A missing
	 * course is left out, as the contract types it as a string.
	 */
	public record Slot(UUID slotId, String name, boolean required, OfferStatus status, int quantity,
			@JsonInclude(NON_NULL) String course, List<Option> options) {

		static Slot of(CompositionSlot slot) {
			return new Slot(slot.getId(), slot.getName(), true, slot.getStatus(), slot.getQuantity(), slot.getCourse(),
					slot.getOptions().stream().map(Option::of).toList());
		}

	}

	public record Option(UUID optionId, SourceType sourceType, UUID recipeId, UUID inventoryItemId, OfferStatus status,
			String displayName, BigDecimal quantity, String unit, Source source) {

		static Option of(SlotOption option) {
			return new Option(option.getId(), option.getSourceType(), null, option.getInventoryItemId(),
					option.getStatus(), option.getDisplayName(), option.getQuantity(), option.getUnit(),
					new Source(option.getInventoryItemId(), option.getSourceName()));
		}

	}

	/** Read-only reference to the content source. */
	public record Source(UUID id, String name) {
	}

	public record OfferListResponse(List<OfferSummary> data, PageMeta meta) {
	}

}
