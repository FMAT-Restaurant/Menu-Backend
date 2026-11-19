package com.fmatrestaurant.menu.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.application.service.DerivedAvailability.OfferAvailability;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.CatalogOffer;
import com.fmatrestaurant.menu.domain.model.Category;
import com.fmatrestaurant.menu.domain.model.EntryStatus;
import com.fmatrestaurant.menu.domain.model.OfferStatus;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CategoryRepository;

/**
 * The catalog as whoever consults it with "Ver catálogo" sees it (RF-CONS-01 to RF-CONS-03): only ACTIVE entries,
 * their ACTIVE offers and, in each composition, the ACTIVE options. What is not available is still presented, with
 * {@code isAvailable} false, so the client shows it disabled.
 */
@Service
@Transactional(readOnly = true)
public class CatalogViewService {

	private static final Comparator<CatalogOffer> BY_TAG = Comparator.comparing(CatalogOffer::getPresentationTag)
			.thenComparing(CatalogOffer::getId);

	private final CatalogEntryService entryService;

	private final CatalogEntryRepository entryRepository;

	private final CatalogOfferRepository offerRepository;

	private final CategoryRepository categoryRepository;

	private final DerivedAvailability availability;

	public CatalogViewService(CatalogEntryService entryService, CatalogEntryRepository entryRepository,
			CatalogOfferRepository offerRepository, CategoryRepository categoryRepository,
			DerivedAvailability availability) {
		this.entryService = entryService;
		this.entryRepository = entryRepository;
		this.offerRepository = offerRepository;
		this.categoryRepository = categoryRepository;
		this.availability = availability;
	}

	/**
	 * An ACTIVE entry of the page with its ACTIVE offers, in alphabetical order by presentation tag.
	 *
	 * @param available whether at least one of its ACTIVE offers is available (RF-DISP-04.CA1)
	 */
	public record CatalogEntryView(CatalogEntry entry, boolean available, List<CatalogOfferView> offers) {
	}

	/** An ACTIVE offer and the availability of the offer, its slots and its options. */
	public record CatalogOfferView(CatalogOffer offer, OfferAvailability availability) {
	}

	public record CatalogPage(Page<CatalogEntry> page, List<CatalogEntryView> entries) {
	}

	/** A category of the menu and the number of ACTIVE entries it classifies. */
	public record CatalogCategory(Category category, long entryCount) {
	}

	/** Every category of the menu in alphabetical order, and the ACTIVE entries without categories. */
	public record CatalogCategories(List<CatalogCategory> categories, long uncategorized) {
	}

	/**
	 * A page of ACTIVE entries in alphabetical order by brand name. {@code q} searches the brand name, the
	 * description and the names of the categories; {@code categoryId} is a category or {@code __uncategorized__}.
	 */
	public CatalogPage page(String q, String categoryId, int page, int pageSize) {
		Page<CatalogEntry> entries = entryService.list(q, categoryId, EntryStatus.ACTIVE, page, pageSize);
		List<UUID> ids = entries.getContent().stream().map(CatalogEntry::getId).toList();
		Map<UUID, List<CatalogOffer>> offersByEntry = new HashMap<>();
		List<CatalogOffer> active = ids.isEmpty() ? List.of()
				: offerRepository.findByEntryIdIn(ids).stream()
						.filter(offer -> offer.getStatus() == OfferStatus.ACTIVE).sorted(BY_TAG).toList();
		active.forEach(offer -> offersByEntry.computeIfAbsent(offer.getEntry().getId(), _ -> new ArrayList<>())
				.add(offer));
		Map<UUID, OfferAvailability> offerAvailability = availability.offers(active);
		List<CatalogEntryView> views = entries.getContent().stream().map(entry -> {
			List<CatalogOfferView> offers = offersByEntry.getOrDefault(entry.getId(), List.of()).stream()
					.map(offer -> new CatalogOfferView(offer, offerAvailability.get(offer.getId()))).toList();
			boolean available = offers.stream().anyMatch(offer -> offer.availability().available());
			return new CatalogEntryView(entry, available, offers);
		}).toList();
		return new CatalogPage(entries, views);
	}

	/** Also the categories that classify no entry (RF-CONS-01.CA4); entryCount only counts ACTIVE entries. */
	public CatalogCategories categories() {
		Long menuId = CategoryService.DEFAULT_MENU_ID;
		Map<UUID, Long> counts = new HashMap<>();
		entryRepository.countEntriesByCategoryAndStatus(menuId, EntryStatus.ACTIVE)
				.forEach(count -> counts.put(count.getCategoryId(), count.getEntryCount()));
		List<CatalogCategory> categories = categoryRepository.findByMenuIdOrderByNameAscIdAsc(menuId).stream()
				.map(category -> new CatalogCategory(category, counts.getOrDefault(category.getId(), 0L)))
				.toList();
		return new CatalogCategories(categories,
				entryRepository.countByMenuIdAndStatusAndCategoriesIsEmpty(menuId, EntryStatus.ACTIVE));
	}

}
