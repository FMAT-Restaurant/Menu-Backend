package com.fmatrestaurant.menu.api.dto;

/**
 * Pagination metadata of a list response.
 */
public record PageMeta(int page, int pageSize, long total, int totalPages) {
}
