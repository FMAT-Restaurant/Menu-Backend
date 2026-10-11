package com.fmatrestaurant.menu.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fmatrestaurant.menu.domain.InventoryItem;

/**
 * Reads articles from the Inventory service, which owns their identity, unit and stock
 * (BR-MENU-018). Menu never changes them.
 *
 * <p>ponytail: Inventory has no published contract yet; this assumes it serves
 * {@code GET /inventory/items} and {@code GET /inventory/items/{id}} with the shapes of the Menu
 * contract. Adjust the paths when that contract exists.
 *
 * @throws RestClientException from every method when Inventory cannot answer
 */
@Component
public class InventoryClient {

	private final RestClient restClient;

	@Autowired
	public InventoryClient(@Value("${menu.inventory.base-url:http://localhost:8081/api/v1}") String baseUrl) {
		this(RestClient.builder().baseUrl(baseUrl));
	}

	InventoryClient(RestClient.Builder builder) {
		this.restClient = builder.build();
	}

	/** Empty when Inventory does not know the article. */
	public Optional<InventoryItem> find(UUID id) {
		try {
			ItemResponse response = restClient.get().uri("/inventory/items/{id}", id).retrieve()
					.body(ItemResponse.class);
			return Optional.ofNullable(response).map(ItemResponse::data).map(Item::toDomain);
		} catch (HttpClientErrorException.NotFound _) {
			return Optional.empty();
		}
	}

	public InventoryItemPage search(String q, int page, int pageSize) {
		ItemListResponse response = restClient.get()
				.uri(uri -> uri.path("/inventory/items").queryParamIfPresent("q", Optional.ofNullable(q))
						.queryParam("page", page).queryParam("pageSize", pageSize).build())
				.retrieve().body(ItemListResponse.class);
		if (response == null || response.data() == null || response.meta() == null) {
			throw new RestClientException("Inventory answered without items");
		}
		return new InventoryItemPage(response.data().stream().map(Item::toDomain).toList(), response.meta());
	}

	public record InventoryItemPage(List<InventoryItem> data, Meta meta) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Meta(int page, int pageSize, long total, int totalPages) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Item(UUID id, String name, String unit) {

		InventoryItem toDomain() {
			return new InventoryItem(id, name, unit);
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ItemResponse(Item data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ItemListResponse(List<Item> data, Meta meta) {
	}

}
