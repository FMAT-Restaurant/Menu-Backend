package com.fmatrestaurant.menu;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test JWTs ({@code alg: none}, empty signature) with the {@code permissions} each test asks for. Menu does
 * not verify signatures until BE-12, so these are enough to exercise authorization.
 */
public final class TestTokens {

	/** The 13 permissions of Menu (fixture TK-ADMIN). */
	public static final List<String> ALL_PERMISSIONS = List.of("menu:catalog:read", "menu:catalog:print",
			"menu:categories:read", "menu:categories:write", "menu:entries:read", "menu:entries:write",
			"menu:entries:status", "menu:offers:read", "menu:offers:write", "menu:offers:status",
			"menu:recipes:read", "menu:recipes:write", "menu:images:write");

	/** Fixture TK-ADMIN: every permission of Menu. */
	public static final String ADMIN = with(ALL_PERMISSIONS.toArray(String[]::new));

	/** Fixture TK-CATALOGO: only {@code menu:catalog:read}. */
	public static final String CATALOG = with("menu:catalog:read");

	/** Fixture TK-SIN-PERMISOS: an empty {@code permissions}. */
	public static final String NONE = with();

	/** The administrators' wildcard. */
	public static final String WILDCARD = with("*");

	/** A JWT without the {@code permissions} claim: it is treated as an empty list. */
	public static final String WITHOUT_PERMISSIONS = token("{\"sub\":\"test\"}");

	/** A JWT whose {@code permissions} is not an array of strings: it is answered with 401. */
	public static final String MALFORMED_PERMISSIONS = token("{\"sub\":\"test\",\"permissions\":\"menu:entries:read\"}");

	private TestTokens() {
	}

	/** A JWT with exactly these permissions. */
	public static String with(String... permissions) {
		String list = Arrays.stream(permissions).map(permission -> "\"" + permission + "\"")
				.collect(Collectors.joining(",", "[", "]"));
		return token("{\"sub\":\"test\",\"permissions\":" + list + "}");
	}

	/** Every permission of Menu but the given ones. */
	public static String allBut(String... excluded) {
		List<String> left = ALL_PERMISSIONS.stream().filter(permission -> !List.of(excluded).contains(permission))
				.toList();
		return with(left.toArray(String[]::new));
	}

	/** The value of the {@code Authorization} header for a token. */
	public static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String token(String payload) {
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		return encoder.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
	}

}
