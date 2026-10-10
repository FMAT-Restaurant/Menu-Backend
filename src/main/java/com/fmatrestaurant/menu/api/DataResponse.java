package com.fmatrestaurant.menu.api;

/**
 * Envelope of a response that contains a single resource.
 */
public record DataResponse<T>(T data) {
}
