package com.fmatrestaurant.menu.api.controller;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fmatrestaurant.menu.api.MenuApi;
import com.fmatrestaurant.menu.api.controller.ImageController.ImageResponse;
import com.fmatrestaurant.menu.api.dto.PageMeta;
import com.fmatrestaurant.menu.application.service.CatalogViewService;
import com.fmatrestaurant.menu.application.service.CatalogViewService.CatalogCategories;
import com.fmatrestaurant.menu.application.service.CatalogViewService.CatalogEntryView;
import com.fmatrestaurant.menu.application.service.CatalogViewService.CatalogOfferView;
import com.fmatrestaurant.menu.application.service.CatalogViewService.CatalogPage;
import com.fmatrestaurant.menu.application.service.DerivedAvailability.OfferAvailability;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.CatalogOffer;
import com.fmatrestaurant.menu.domain.model.Category;
import com.fmatrestaurant.menu.domain.model.CompositionSlot;
import com.fmatrestaurant.menu.domain.model.OfferStatus;
import com.fmatrestaurant.menu.domain.model.SlotOption;

/**
 * The visible catalog (getMenuCatalog, listMenuCatalogCategories), for whoever has "Ver catálogo". It only exposes
 * what the client presents: no administrative status, source or default selection (RF-CONS-02.CA2,
 * RF-COMP-06.CA2). The ETag and the 304 answers come from the body ({@code WebConfiguration}).
 */
@MenuApi("/menu/catalog")
public class CatalogController {

	private static final Comparator<Category> BY_NAME = Comparator.comparing(Category::getName)
			.thenComparing(Category::getId);

	private final CatalogViewService catalog;

	public CatalogController(CatalogViewService catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	public CatalogEntryPageResponse entries(@RequestParam(required = false) String q,
			@RequestParam(required = false) String categoryId,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		CatalogPage result = catalog.page(q, categoryId, page, pageSize);
		return new CatalogEntryPageResponse(result.entries().stream().map(CatalogViewEntry::of).toList(),
				new PageMeta(page, pageSize, result.page().getTotalElements(), result.page().getTotalPages()));
	}

	@GetMapping("/categories")
	public CatalogCategoryListResponse categories() {
		CatalogCategories result = catalog.categories();
		return new CatalogCategoryListResponse(result.categories().stream()
				.map(category -> new CatalogCategoryResponse(category.category().getId(), category.category().getName(),
						category.category().getDescription(), category.entryCount()))
				.toList(), new Uncategorized(result.uncategorized()));
	}

	public record CatalogEntryPageResponse(List<CatalogViewEntry> data, PageMeta meta) {
	}

	/** An ACTIVE entry; {@code offers} is empty when it has no ACTIVE offer, and then it is not available. */
	public record CatalogViewEntry(UUID id, String brandName, String description, ImageResponse image,
			List<UUID> categoryIds, @JsonProperty("isAvailable") boolean isAvailable, List<CatalogViewOffer> offers) {

		static CatalogViewEntry of(CatalogEntryView view) {
			CatalogEntry entry = view.entry();
			return new CatalogViewEntry(entry.getId(), entry.getBrandName(), entry.getDescription(),
					ImageResponse.of(entry.getImageId()),
					entry.getCategories().stream().sorted(BY_NAME).map(Category::getId).toList(), view.available(),
					view.offers().stream().map(CatalogViewOffer::of).toList());
		}

	}

	/** An ACTIVE offer, a product sold on its own; its visible name is the brand name plus the tag. */
	public record CatalogViewOffer(UUID id, String presentationTag, BigDecimal basePrice, ImageResponse image,
			@JsonProperty("isAvailable") boolean isAvailable, CatalogViewComposition composition) {

		static CatalogViewOffer of(CatalogOfferView view) {
			CatalogOffer offer = view.offer();
			OfferAvailability available = view.availability();
			return new CatalogViewOffer(offer.getId(), offer.getPresentationTag(), offer.getBasePrice(),
					ImageResponse.of(offer.getImageId()), available.available(), new CatalogViewComposition(
							offer.getSlots().stream().map(slot -> CatalogViewSlot.of(slot, available)).toList()));
		}

	}

	public record CatalogViewComposition(List<CatalogViewSlot> slots) {
	}

	/** Every slot, in the order of its rounds, with its course and only its ACTIVE options, in order. */
	public record CatalogViewSlot(UUID slotId, String name, int quantity, @JsonInclude(NON_NULL) String course,
			@JsonProperty("isAvailable") boolean isAvailable, List<CatalogViewOption> options) {

		static CatalogViewSlot of(CompositionSlot slot, OfferAvailability available) {
			return new CatalogViewSlot(slot.getId(), slot.getName(), slot.getQuantity(), slot.getCourse(),
					available.slot(slot.getId()),
					slot.getOptions().stream().filter(option -> option.getStatus() == OfferStatus.ACTIVE)
							.map(option -> CatalogViewOption.of(option, available)).toList());
		}

	}

	public record CatalogViewOption(UUID optionId, String displayName,
			@JsonProperty("isAvailable") boolean isAvailable) {

		static CatalogViewOption of(SlotOption option, OfferAvailability available) {
			return new CatalogViewOption(option.getId(), option.getDisplayName(), available.option(option.getId()));
		}

	}

	public record CatalogCategoryListResponse(List<CatalogCategoryResponse> data, Uncategorized uncategorized) {
	}

	/** {@code entryCount} counts only the ACTIVE entries, the ones whoever consults the catalog sees. */
	public record CatalogCategoryResponse(UUID id, String name, @JsonInclude(NON_NULL) String description,
			long entryCount) {
	}

	public record Uncategorized(long entryCount) {
	}

}
