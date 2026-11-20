package com.fmatrestaurant.menu.api.controller;

import com.fmatrestaurant.menu.api.MenuApi;
import java.util.Comparator;
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fmatrestaurant.menu.api.controller.ImageController.ImageResponse;
import com.fmatrestaurant.menu.application.service.CatalogEntryService;
import com.fmatrestaurant.menu.application.service.DerivedAvailability;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.Category;
import com.fmatrestaurant.menu.domain.model.EntryStatus;

/**
 * Administration of catalog entries. Business rules live in {@link CatalogEntryService} and
 * {@link CatalogEntry}.
 */
@MenuApi("/menu/entries")
public class CatalogEntryController {

	private static final Comparator<Category> BY_NAME = Comparator.comparing(Category::getName)
			.thenComparing(Category::getId);

	private final CatalogEntryService entryService;

	private final DerivedAvailability availability;

	public CatalogEntryController(CatalogEntryService entryService, DerivedAvailability availability) {
		this.entryService = entryService;
		this.availability = availability;
	}

	/** The initial status is always INACTIVE; the request has no {@code status}. */
	@PostMapping
	public ResponseEntity<DataResponse<CatalogEntryDetail>> create(@RequestBody CatalogEntryCreateRequest request) {
		CatalogEntry entry = entryService.create(request.brandName(), request.description(), request.categoryIds(),
				request.imageId());
		boolean available = availability.entry(entry.getId());
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(entry, available))
				.body(new DataResponse<>(CatalogEntryDetail.of(entry, available)));
	}

	@GetMapping
	public CatalogEntryListResponse list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String categoryId,
			@RequestParam(required = false) EntryStatus status,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		Page<CatalogEntry> entries = entryService.list(q, categoryId, status, page, pageSize);
		List<UUID> ids = entries.getContent().stream().map(CatalogEntry::getId).toList();
		Map<UUID, Long> offerCounts = entryService.offerCounts(ids);
		Map<UUID, Boolean> available = availability.entries(ids);
		return new CatalogEntryListResponse(
				entries.getContent().stream()
						.map(entry -> CatalogEntrySummary.of(entry, offerCounts.getOrDefault(entry.getId(), 0L),
								available.getOrDefault(entry.getId(), false)))
						.toList(),
				new PageMeta(page, pageSize, entries.getTotalElements(), entries.getTotalPages()));
	}

	/** Spring answers 304 when If-None-Match matches the ETag of the response. */
	@GetMapping("/{entryId}")
	public ResponseEntity<DataResponse<CatalogEntryDetail>> get(@PathVariable UUID entryId) {
		return detail(entryService.get(entryId));
	}

	@PatchMapping("/{entryId}")
	public ResponseEntity<DataResponse<CatalogEntryDetail>> update(@PathVariable UUID entryId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestBody CatalogEntryPatchRequest request) {
		CatalogEntry entry = entryService.update(entryId, Revisions.strongVersion(ifMatch), request.brandName(),
				request.description(), request.status(), request.categoryIds());
		return detail(entry);
	}

	/** Deletes an ARCHIVED entry and its offers; any other status is answered with 409. */
	@DeleteMapping("/{entryId}")
	public ResponseEntity<Void> delete(@PathVariable UUID entryId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
		entryService.delete(entryId, Revisions.strongVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<DataResponse<CatalogEntryDetail>> detail(CatalogEntry entry) {
		boolean available = availability.entry(entry.getId());
		return ResponseEntity.ok().eTag(etag(entry, available))
				.body(new DataResponse<>(CatalogEntryDetail.of(entry, available)));
	}

	/**
	 * Strong, as the contract requires for If-Match; it also changes with the derived availability of the entry,
	 * which follows its offers without a new version: a when it is available, u when it is not.
	 */
	private static String etag(CatalogEntry entry, boolean available) {
		return Revisions.strong(entry.getVersion(), available ? "a" : "u");
	}

	/** Body of POST; without {@code categoryIds}, the entry has no categories. */
	public record CatalogEntryCreateRequest(String brandName, String description, List<UUID> categoryIds,
			UUID imageId) {
	}

	/** Body of PATCH; missing values keep the current ones. The image is fixed at creation. */
	public record CatalogEntryPatchRequest(String brandName, String description, EntryStatus status,
			List<UUID> categoryIds) {
	}

	public record CatalogEntryDetail(UUID id, String brandName, String description, EntryStatus status,
			ImageResponse image, List<UUID> categoryIds, @JsonProperty("isAvailable") boolean isAvailable) {

		static CatalogEntryDetail of(CatalogEntry entry, boolean available) {
			return new CatalogEntryDetail(entry.getId(), entry.getBrandName(), entry.getDescription(),
					entry.getStatus(), ImageResponse.of(entry.getImageId()),
					entry.getCategories().stream().sorted(BY_NAME).map(Category::getId).toList(), available);
		}

	}

	public record CategoryReference(UUID id, String name) {
	}

	public record CatalogEntrySummary(UUID id, String brandName, String description, EntryStatus status,
			ImageResponse image, List<CategoryReference> categories, int offerCount,
			@JsonProperty("isAvailable") boolean isAvailable) {

		static CatalogEntrySummary of(CatalogEntry entry, long offerCount, boolean available) {
			return new CatalogEntrySummary(entry.getId(), entry.getBrandName(), entry.getDescription(),
					entry.getStatus(), ImageResponse.of(entry.getImageId()),
					entry.getCategories().stream().sorted(BY_NAME)
							.map(category -> new CategoryReference(category.getId(), category.getName())).toList(),
					(int) offerCount, available);
		}

	}

	public record CatalogEntryListResponse(List<CatalogEntrySummary> data, PageMeta meta) {
	}

}
