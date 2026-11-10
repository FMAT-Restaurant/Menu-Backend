package com.fmatrestaurant.menu.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.domain.model.InventoryItem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Synchronous client of the Inventory service ({@code GET {base-url}/items/{id}}). Menu only reads
 * articles; it never creates, changes or deletes them.
 *
 * <p>This is the service-to-service route, not the one the frontend uses to search articles. Inventory answers
 * with the article as a flat {@code {id, name, unit}} object, not wrapped in a {@code data} envelope.
 *
 * <p>Only a {@code 404} that carries the error envelope of the Inventory contract confirms that an article
 * does not exist. Every other failure (timeout, connection, a non-2xx answer such as 401, 403, 429 or 5xx,
 * a {@code 404} without that envelope, which is what a wrong base URL or a proxy answers, or an invalid
 * body) is a {@link RestClientException}, so it is never mistaken for an article that does not exist.
 */
@Component
public class InventoryClient {

	private static final JsonMapper ENVELOPE = JsonMapper.builder().build();

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
	 * @return empty only when Inventory confirms with a {@code 404} and its error envelope that no article with
	 *         the given id exists; never when Inventory cannot be reached or does not answer as its contract defines
	 * @throws RestClientException if Inventory cannot be reached or does not answer as its contract defines
	 */
	public Optional<InventoryItem> find(String id) {
		if (blank(id)) {
			throw new IllegalArgumentException("id cannot be blank");
		}
		ResponseEntity<InventoryResponse> response;
		try {
			response = restClient.get()
					.uri("/items/{id}", id)
					.retrieve()
					.toEntity(InventoryResponse.class);
		}
		catch (HttpClientErrorException.NotFound e) {
			if (hasErrorEnvelope(e.getResponseBodyAsByteArray())) {
				return Optional.empty();
			}
			throw e;
		}
		InventoryResponse result = response.getBody();
		if (result == null) {
			throw new RestClientException("Inventory response must contain the article");
		}
		if (!id.equals(result.id()) || blank(result.name()) || blank(result.unit())) {
			throw new RestClientException("Inventory item must contain the requested id, name and unit");
		}
		return Optional.of(new InventoryItem(result.id(), result.name(), result.unit()));
	}

	/**
	 * The article as Inventory returns it: a flat object, not wrapped in an envelope. Only the fields Menu
	 * needs are read; every other one (sku, stock, cost, timestamps...) is ignored.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record InventoryResponse(String id, String name, String unit) {
	}

	/** Whether the body is the error envelope of the contract: {@code {"error": {"message": "..."}}}. */
	private static boolean hasErrorEnvelope(byte[] body) {
		if (body == null || body.length == 0) {
			return false;
		}
		try {
			JsonNode error = ENVELOPE.readTree(body).path("error");
			return error.isObject() && error.path("message").isString();
		}
		catch (JacksonException _) {
			return false;
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
