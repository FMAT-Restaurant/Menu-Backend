package com.fmatrestaurant.menu.api;

/**
 * Pagination metadata of a list response.
 */
public record PageMeta(int page, int pageSize, long total, int totalPages) {
}
