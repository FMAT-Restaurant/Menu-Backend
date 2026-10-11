package com.fmatrestaurant.menu.api;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.InvalidFieldException;

import tools.jackson.databind.JsonNode;

/**
 * HTTP operations for menu categories. Business rules and persistence live in {@link CategoryService}.
 */
@RestController
@RequestMapping("/api/v1/menu/categories")
public class CategoryController {

	private static final Pattern ETAG = Pattern.compile("\"rev-(\\d+)\"");

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	public CategoryListResponse list(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		CategoryService.CategoryPage categories = categoryService.list(page, pageSize);
		return new CategoryListResponse(categories.page().getContent().stream()
				.map(category -> CategoryItem.of(category,
						categories.entryCounts().getOrDefault(category.getId(), 0L)))
				.toList(),
				new PageMeta(page, pageSize, categories.page().getTotalElements(), categories.page().getTotalPages()));
	}

	@GetMapping("/{categoryId}")
	public ResponseEntity<DataResponse<CategoryItem>> get(@PathVariable UUID categoryId) {
		Category category = categoryService.get(categoryId);
		return ResponseEntity.ok().eTag(etag(category))
				.body(new DataResponse<>(CategoryItem.of(category, categoryService.entryCount(categoryId))));
	}

	@DeleteMapping("/{categoryId}")
	public ResponseEntity<Void> delete(@PathVariable UUID categoryId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
		Category category = categoryService.delete(categoryId, version(ifMatch));
		return ResponseEntity.noContent().eTag(etag(category)).build();
	}

	@PostMapping
	public ResponseEntity<DataResponse<CategoryItem>> create(@RequestBody CategoryRequest request) {
		Category category = categoryService.create(request.name(), request.description());
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(category))
				.body(new DataResponse<>(CategoryItem.of(category, 0)));
	}

	@PatchMapping("/{categoryId}")
	public ResponseEntity<DataResponse<CategoryItem>> update(@PathVariable UUID categoryId,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestBody JsonNode request) {
		if (request == null || !request.isObject()) {
			throw new InvalidFieldException("/", "The request body must be an object");
		}
		boolean updateName = request.has("name");
		boolean updateDescription = request.has("description");
		String name = textValue(request, "name", updateName);
		String description = textValue(request, "description", updateDescription);
		Category category = categoryService.update(categoryId, version(ifMatch), name, updateName,
				description, updateDescription);
		return ResponseEntity.ok().eTag(etag(category))
				.body(new DataResponse<>(CategoryItem.of(category, categoryService.entryCount(categoryId))));
	}

	private static String textValue(JsonNode request, String field, boolean provided) {
		if (!provided) {
			return null;
		}
		JsonNode value = request.get(field);
		if (value == null || !value.isString()) {
			throw new InvalidFieldException("/" + field, "The category " + field + " must be a string");
		}
		return value.stringValue();
	}

	private static String etag(Category category) {
		long version = category.getVersion() == null ? 0 : category.getVersion();
		return "\"rev-" + version + "\"";
	}

	/** A malformed validator never matches the current resource version. */
	private static long version(String ifMatch) {
		Matcher matcher = ETAG.matcher(ifMatch.strip());
		if (!matcher.matches()) {
			return -1;
		}
		try {
			return Long.parseLong(matcher.group(1));
		} catch (NumberFormatException _) {
			return -1;
		}
	}

	public record CategoryRequest(String name, String description) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CategoryItem(UUID id, String name, String description, long entryCount, long version) {

		static CategoryItem of(Category category, long entryCount) {
			return new CategoryItem(category.getId(), category.getName(), category.getDescription(), entryCount,
					category.getVersion() == null ? 0 : category.getVersion());
		}

	}

	public record CategoryListResponse(List<CategoryItem> data, PageMeta meta) {
	}

}
