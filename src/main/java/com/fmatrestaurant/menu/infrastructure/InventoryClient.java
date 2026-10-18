package com.fmatrestaurant.menu.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.domain.InventoryItem;

/**
 * Synchronous client of the Inventory service ({@code GET /inventory/items/{id}}). Menu only reads
 * articles; it never creates, changes or deletes them.
 *
 * <p>Every failure (timeout, connection, a non-2xx answer such as 401, 403 or 429, or an invalid body) is
 * a {@link RestClientException}, so it is never mistaken for an article that does not exist.
 */
@Component
public class InventoryClient {

	private final RestClient restClient;

	public InventoryClient(@Value("${menu.inventory.base-url}") String baseUrl,
			@Value("${menu.inventory.token:}") String token,
			@Value("${menu.inventory.timeout}") Duration timeout) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(timeout).build());
		requestFactory.setReadTimeout(timeout);
		RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
		if (!token.isBlank()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		this.restClient = builder.build();
	}

	/**
	 * The article with the given id, if it exists. If Inventory cannot be reached or does not answer as its contract defines, a {@link RestClientException} is thrown.
	 *
	 * @return empty only when no article with the given id exists; never when Inventory cannot be reached or does not answer as its contract defines
	 * @throws RestClientException if Inventory cannot be reached or does not answer as its contract defines
	 */
	public Optional<InventoryItem> find(String id) {
		if (blank(id)) {
			throw new IllegalArgumentException("id cannot be blank");
		}
		ResponseEntity<InventoryResponse> response = restClient.get()
				.uri("/inventory/items/{id}", id)
				.retrieve().toEntity(InventoryResponse.class);
		if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
			return Optional.empty();
		}
		InventoryResponse result = response.getBody();
		if (result == null || result.data() == null) {
			throw new RestClientException("Inventory response must contain data");
		}
		InventoryItem item = result.data();
		if (!id.equals(item.id()) || blank(item.name()) || blank(item.unit())) {
			throw new RestClientException("Inventory item must contain the requested id, name and unit");
		}
		return Optional.of(item);
	}

	public record InventoryResponse(InventoryItem data) {
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
