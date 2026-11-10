package com.fmatrestaurant.menu.api;

/**
 * The only place that says where the API lives and which version it serves. Controllers declare their
 * routes relative to this prefix through {@link MenuApi}; only the servlet filters, which run before Spring
 * MVC, need the literal {@link #BASE}.
 */
public final class ApiVersioning {

	/** Segment before the version, shared by every version of the API. */
	public static final String PREFIX = "/api";

	/** Version served by {@link MenuApi}; it is the second segment of the path. */
	public static final String CURRENT = "v1";

	/** Literal base path of the current version, for what is not resolved by Spring MVC. */
	public static final String BASE = PREFIX + "/" + CURRENT;

	private ApiVersioning() {
	}
}
