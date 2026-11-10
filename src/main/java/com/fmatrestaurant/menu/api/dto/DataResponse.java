package com.fmatrestaurant.menu.api.dto;

/**
 * Envelope of a response that contains a single resource.
 */
public record DataResponse<T>(T data) {
}
