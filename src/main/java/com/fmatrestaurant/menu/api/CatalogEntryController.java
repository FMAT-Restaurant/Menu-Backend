package com.fmatrestaurant.menu.api;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.fmatrestaurant.menu.api.ImageController.ImageResponse;
import com.fmatrestaurant.menu.application.CatalogEntryService;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.EntryStatus;

/**
 * Administration of catalog entries. Business rules live in {@link CatalogEntryService} and
 * {@link CatalogEntry}.
 */
@RestController
@RequestMapping("/api/v1/menu/entries")
public class CatalogEntryController {

	private static final Pattern ETAG = Pattern.compile("(?:W/)?\"rev-(\\d+)\"");

	private static final Comparator<Category> BY_NAME = Comparator.comparing(Category::getName)
			.thenComparing(Category::getId);

	private final CatalogEntryService entryService;

	public CatalogEntryController(CatalogEntryService entryService) {
		this.entryService = entryService;
	}

	/** The initial status is always INACTIVE: a {@code status} sent by the client is ignored. */
	@PostMapping
	public ResponseEntity<DataResponse<CatalogEntryDetail>> create(@RequestBody CatalogEntryRequest request) {
		CatalogEntry entry = entryService.create(request.brandName(), request.description(), request.categoryIds(),
				request.imageId());
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(entry))
				.body(new DataResponse<>(CatalogEntryDetail.of(entry)));
	}

	@GetMapping
	public CatalogEntryListResponse list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String categoryId,
			@RequestParam(required = false) EntryStatus status,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		Page<CatalogEntry> entries = entryService.list(q, categoryId, status, page, pageSize);
		return new CatalogEntryListResponse(
				entries.getContent().stream().map(CatalogEntrySummary::of).toList(),
				new PageMeta(page, pageSize, entries.getTotalElements(), entries.getTotalPages()));
	}

	/** Spring answers 304 when If-None-Match matches the ETag of the response. */
	@GetMapping("/{entryId}")
	public ResponseEntity<DataResponse<CatalogEntryDetail>> get(@PathVariable UUID entryId) {
		CatalogEntry entry = entryService.get(entryId);
		return ResponseEntity.ok().eTag(etag(entry)).body(new DataResponse<>(CatalogEntryDetail.of(entry)));
	}

	@PatchMapping("/{entryId}")
	public ResponseEntity<DataResponse<CatalogEntryDetail>> update(@PathVariable UUID entryId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestBody CatalogEntryRequest request) {
		CatalogEntry entry = entryService.update(entryId, version(ifMatch), request.brandName(),
				request.description(), request.status(), request.categoryIds(), request.imageId());
		return ResponseEntity.ok().eTag(etag(entry)).body(new DataResponse<>(CatalogEntryDetail.of(entry)));
	}

	private static String etag(CatalogEntry entry) {
		return "W/\"rev-" + entry.getVersion() + "\"";
	}

	/** An If-Match that is not an ETag of this API never matches, so it ends in 412. */
	private static long version(String ifMatch) {
		Matcher matcher = ETAG.matcher(ifMatch.strip());
		return matcher.matches() ? Long.parseLong(matcher.group(1)) : -1;
	}

	/** Body of POST and PATCH; on PATCH, missing values keep the current ones. */
	public record CatalogEntryRequest(String brandName, String description, EntryStatus status,
			List<UUID> categoryIds, UUID imageId) {
	}

	public record CatalogEntryDetail(UUID id, String brandName, String description, EntryStatus status,
			ImageResponse image, List<UUID> categoryIds) {

		static CatalogEntryDetail of(CatalogEntry entry) {
			return new CatalogEntryDetail(entry.getId(), entry.getBrandName(), entry.getDescription(),
					entry.getStatus(), ImageResponse.of(entry.getImageId()),
					entry.getCategories().stream().sorted(BY_NAME).map(Category::getId).toList());
		}

	}

	public record CategoryReference(UUID id, String name) {
	}

	public record CatalogEntrySummary(UUID id, String brandName, String description, EntryStatus status,
			ImageResponse image, List<CategoryReference> categories, int offerCount) {

		static CatalogEntrySummary of(CatalogEntry entry) {
			return new CatalogEntrySummary(entry.getId(), entry.getBrandName(), entry.getDescription(),
					entry.getStatus(), ImageResponse.of(entry.getImageId()),
					entry.getCategories().stream().sorted(BY_NAME)
							.map(category -> new CategoryReference(category.getId(), category.getName())).toList(),
					// ponytail: offers arrive with MVP1-T15; count them from the offer repository then.
					0);
		}

	}

	public record CatalogEntryListResponse(List<CatalogEntrySummary> data, PageMeta meta) {
	}

}
