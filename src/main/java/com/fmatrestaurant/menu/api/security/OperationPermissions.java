package com.fmatrestaurant.menu.api.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * The permissions each operation of the Menu API requires, as its {@code x-required-permission} in the
 * contract declares them (RF-GEN-01.CA1, CA5). {@code OperationPermissionsContractTest} keeps both in sync.
 * Paths are relative to {@code /api/v1}.
 */
public final class OperationPermissions {

	public static final String CATALOG_READ = "menu:catalog:read";
	public static final String CATALOG_PRINT = "menu:catalog:print";
	public static final String CATEGORIES_READ = "menu:categories:read";
	public static final String CATEGORIES_WRITE = "menu:categories:write";
	public static final String ENTRIES_READ = "menu:entries:read";
	public static final String ENTRIES_WRITE = "menu:entries:write";
	public static final String ENTRIES_STATUS = "menu:entries:status";
	public static final String OFFERS_READ = "menu:offers:read";
	public static final String OFFERS_WRITE = "menu:offers:write";
	public static final String OFFERS_STATUS = "menu:offers:status";
	public static final String RECIPES_READ = "menu:recipes:read";
	public static final String RECIPES_WRITE = "menu:recipes:write";
	public static final String IMAGES_WRITE = "menu:images:write";

	/**
	 * How the permissions of an operation apply: {@code FIXED} requires all of them; {@code ENTRY_PATCH} and
	 * {@code OFFER_PATCH} hold the management and the status permission, and the body decides which apply
	 * (RF-GEN-01.CA3).
	 */
	public enum Kind {
		FIXED, ENTRY_PATCH, OFFER_PATCH
	}

	/** An operation by its method and path pattern, and the permissions it requires. */
	public record Rule(HttpMethod method, String path, Set<String> permissions, Kind kind) {

		private static final PathPatternParser PARSER = new PathPatternParser();

		PathPattern pattern() {
			return PARSER.parse(path);
		}

	}

	public static final List<Rule> RULES = List.of(
			fixed(HttpMethod.GET, "/menu/catalog", CATALOG_READ),
			fixed(HttpMethod.GET, "/menu/catalog/categories", CATALOG_READ),
			fixed(HttpMethod.GET, "/menu/catalog/printable", CATALOG_PRINT),
			fixed(HttpMethod.GET, "/menu/categories", CATEGORIES_READ),
			fixed(HttpMethod.POST, "/menu/categories", CATEGORIES_WRITE),
			fixed(HttpMethod.GET, "/menu/categories/{categoryId}", CATEGORIES_READ),
			fixed(HttpMethod.PATCH, "/menu/categories/{categoryId}", CATEGORIES_WRITE),
			fixed(HttpMethod.DELETE, "/menu/categories/{categoryId}", CATEGORIES_WRITE),
			fixed(HttpMethod.GET, "/menu/entries", ENTRIES_READ),
			fixed(HttpMethod.POST, "/menu/entries", ENTRIES_WRITE),
			fixed(HttpMethod.GET, "/menu/entries/{entryId}", ENTRIES_READ),
			new Rule(HttpMethod.PATCH, "/menu/entries/{entryId}", Set.of(ENTRIES_WRITE, ENTRIES_STATUS),
					Kind.ENTRY_PATCH),
			fixed(HttpMethod.DELETE, "/menu/entries/{entryId}", ENTRIES_WRITE),
			fixed(HttpMethod.GET, "/menu/offers", OFFERS_READ),
			fixed(HttpMethod.POST, "/menu/entries/{entryId}/offers", OFFERS_WRITE),
			fixed(HttpMethod.GET, "/menu/offers/{offerId}", OFFERS_READ),
			new Rule(HttpMethod.PATCH, "/menu/offers/{offerId}", Set.of(OFFERS_WRITE, OFFERS_STATUS),
					Kind.OFFER_PATCH),
			fixed(HttpMethod.DELETE, "/menu/offers/{offerId}", OFFERS_WRITE),
			fixed(HttpMethod.GET, "/menu/recipes", RECIPES_READ),
			fixed(HttpMethod.POST, "/menu/recipes", RECIPES_WRITE),
			fixed(HttpMethod.GET, "/menu/recipes/{recipeId}", RECIPES_READ),
			fixed(HttpMethod.PATCH, "/menu/recipes/{recipeId}", RECIPES_WRITE),
			fixed(HttpMethod.DELETE, "/menu/recipes/{recipeId}", RECIPES_WRITE),
			fixed(HttpMethod.GET, "/menu/recipes/{recipeId}/revisions", RECIPES_READ),
			fixed(HttpMethod.GET, "/menu/recipes/{recipeId}/revisions/{revision}", RECIPES_READ),
			fixed(HttpMethod.POST, "/menu/media/images", IMAGES_WRITE),
			fixed(HttpMethod.GET, "/menu/media/images/{imageId}", IMAGES_WRITE),
			fixed(HttpMethod.PUT, "/menu/media/images/{imageId}", IMAGES_WRITE));

	private OperationPermissions() {
	}

	/**
	 * @param method the HTTP method; HEAD is checked as GET
	 * @param path the path relative to {@code /api/v1}
	 * @return the rule of the operation, or empty if the API has no such operation
	 */
	public static Optional<Rule> find(String method, String path) {
		String effective = "HEAD".equals(method) ? "GET" : method;
		PathContainer container = PathContainer.parsePath(path);
		return RULES.stream()
				.filter(rule -> rule.method().name().equals(effective) && rule.pattern().matches(container))
				.findFirst();
	}

	private static Rule fixed(HttpMethod method, String path, String permission) {
		return new Rule(method, path, Set.of(permission), Kind.FIXED);
	}

}
