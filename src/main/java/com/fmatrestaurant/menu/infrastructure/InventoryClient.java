package com.fmatrestaurant.menu.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.domain.InventoryItem;

/**
 * Synchronous client of the external Inventory service ({@code GET /inventory/items}). Menu only reads
 * articles; it never creates, changes or deletes them.
 *
 * <p>Every failure (timeout, connection, a non-2xx answer such as 401, 403 or 429, or an invalid body) is
 * a {@link RestClientException}, so it is never mistaken for an article that does not exist.
 */
@Component
public class InventoryClient {

	/** Articles requested per page while searching for an id. */
	static final int PAGE_SIZE = 100;

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
	 * The article whose id is exactly the given one, searched through every page of the listing.
	 *
	 * @return empty only when no page contains the article
	 * @throws RestClientException if Inventory cannot be reached or does not answer as its contract defines
	 */
	// ponytail: the contract only defines a paged text search, so this reads every page until the id is
	// found; use a lookup by id once Inventory documents one.
	public Optional<InventoryItem> find(UUID id) {
		int totalPages = 1;
		for (int page = 1; page <= totalPages; page++) {
			ItemPage result = restClient.get()
					.uri("/inventory/items?page={page}&pageSize={pageSize}", page, PAGE_SIZE)
					.retrieve()
					.body(ItemPage.class);
			if (result == null || result.data() == null || result.meta() == null
					|| result.meta().totalPages() == null) {
				throw new RestClientException("Inventory answered without data or meta");
			}
			for (InventoryItem item : result.data()) {
				if (item == null || item.id() == null || blank(item.name()) || blank(item.unit())) {
					throw new RestClientException("Inventory answered an article without id, name or unit");
				}
				if (item.id().equals(id)) {
					return Optional.of(item);
				}
			}
			totalPages = result.meta().totalPages();
		}
		return Optional.empty();
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	record ItemPage(List<InventoryItem> data, Meta meta) {
	}

	record Meta(Integer totalPages) {
	}

}
