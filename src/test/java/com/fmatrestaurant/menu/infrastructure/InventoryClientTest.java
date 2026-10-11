package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.domain.InventoryItem;

class InventoryClientTest {

	private static final UUID ID = UUID.fromString("5c67b890-1a2b-4d3e-8f9a-0b1c2d3e4f5a");

	private MockRestServiceServer server;

	private InventoryClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory/api/v1");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new InventoryClient(builder);
	}

	@Test
	void findsAnArticleAndIgnoresUnknownFields() {
		server.expect(requestTo("http://inventory/api/v1/inventory/items/" + ID))
				.andRespond(withSuccess("""
						{"data": {"id": "%s", "name": "Potatoes", "unit": "g", "stock": 12}}
						""".formatted(ID), MediaType.APPLICATION_JSON));

		assertEquals(Optional.of(new InventoryItem(ID, "Potatoes", "g")), client.find(ID));
	}

	@Test
	void anUnknownArticleIsEmpty() {
		server.expect(requestTo("http://inventory/api/v1/inventory/items/" + ID))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertTrue(client.find(ID).isEmpty());
	}

	@Test
	void searchesArticles() {
		server.expect(requestTo("http://inventory/api/v1/inventory/items?q=pot&page=2&pageSize=5"))
				.andRespond(withSuccess("""
						{"data": [{"id": "%s", "name": "Potatoes", "unit": "g"}],
						 "meta": {"page": 2, "pageSize": 5, "total": 6, "totalPages": 2}}
						""".formatted(ID), MediaType.APPLICATION_JSON));

		InventoryClient.InventoryItemPage page = client.search("pot", 2, 5);

		assertEquals(List.of(new InventoryItem(ID, "Potatoes", "g")), page.data());
		assertEquals(6, page.meta().total());
	}

	@Test
	void failuresOfInventoryAreRestClientExceptions() {
		server.expect(requestTo("http://inventory/api/v1/inventory/items/" + ID)).andRespond(withServerError());

		assertThrows(RestClientException.class, () -> client.find(ID));
	}

}
