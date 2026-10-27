package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.domain.InventoryItem;

/** Exercises real HTTP, optionally against the local Postman simulation. */
class InventoryClientTest {

	@Test
	void resolvesOpaqueIdsAndStillAcceptsUuidStrings() {
		List<String> ids = List.of("INV-beef-001", "000042", "5c67b890-1a2b-4d3e-8f9a-0b1c2d3e4f5a");
		String baseUrl = System.getenv("MENU_INVENTORY_SIMULATION_URL");
		if (baseUrl == null) {
			InventoryStub stub = InventoryStub.INSTANCE;
			stub.reset();
			for (String id : ids) {
				stub.add(new InventoryItem(id, "Ingredient", "unit"));
			}
			baseUrl = stub.baseUrl();
		}
		InventoryClient client = new InventoryClient(baseUrl, "", Duration.ofSeconds(3));

		for (String id : ids) {
			assertEquals(id, client.find(id).orElseThrow().id());
		}
		assertTrue(client.find("missing-item").isEmpty());
		assertTrue(client.find("42").isEmpty());
		assertTrue(client.find("inv-beef-001").isEmpty());
	}

	@Test
	void requestsTheIdDirectlyRegardlessOfCatalogSize() {
		InventoryStub stub = InventoryStub.INSTANCE;
		stub.reset();
		for (int i = 0; i < 100; i++) {
			stub.add(new InventoryItem("filler-" + i, "Filler", "g"));
		}
		stub.add(new InventoryItem("000042", "Target", "unit"));
		InventoryClient client = new InventoryClient(stub.baseUrl(), "", Duration.ofSeconds(3));
		assertEquals("000042", client.find("000042").orElseThrow().id());
		assertEquals(List.of("GET /api/v1/inventory/items/000042"), stub.requests());
		assertTrue(client.find("42").isEmpty());
		assertEquals(List.of("GET /api/v1/inventory/items/000042", "GET /api/v1/inventory/items/42"), stub.requests());
	}

	@Test
	void aNotFoundWithTheErrorEnvelopeConfirmsThatTheArticleDoesNotExist() {
		InventoryStub stub = InventoryStub.INSTANCE;
		stub.reset();
		InventoryClient client = new InventoryClient(stub.baseUrl(), "", Duration.ofSeconds(3));

		stub.fail(404, "{\"error\": {\"message\": \"No se encontró el recurso solicitado.\"}}");

		assertTrue(client.find("target").isEmpty());
		assertEquals(List.of("GET /api/v1/inventory/items/target"), stub.requests());
		stub.reset();
	}

	/** A wrong base URL or a proxy also answers 404, and that must not turn every article into a missing one. */
	@Test
	void aNotFoundWithoutTheErrorEnvelopeDoesNotConfirmAnything() {
		InventoryStub stub = InventoryStub.INSTANCE;
		stub.reset();
		InventoryClient client = new InventoryClient(stub.baseUrl(), "", Duration.ofSeconds(3));

		for (String body : List.of("", "not json", "<html>Not Found</html>", "{}", "{\"data\": null}",
				"{\"error\": null}", "{\"error\": {}}", "{\"error\": \"Not Found\", \"status\": 404}",
				"{\"error\": {\"message\": 7}}")) {
			stub.fail(404, body);
			assertThrows(RestClientException.class, () -> client.find("target"), body);
		}
		stub.reset();
	}

	@Test
	void rejectsFailedOrMalformedResponsesInsteadOfReportingMissingItems() {
		InventoryStub stub = InventoryStub.INSTANCE;
		stub.reset();
		InventoryClient client = new InventoryClient(stub.baseUrl(), "", Duration.ofSeconds(3));
		for (int status : List.of(401, 403, 429, 500, 503)) {
			stub.fail(status, "{\"error\": {\"message\": \"Unavailable\"}}");
			assertThrows(RestClientException.class, () -> client.find("target"));
		}
		stub.fail(204, "");
		assertThrows(RestClientException.class, () -> client.find("target"));
		for (String body : List.of("", "not json", "{\"data\": []}", "{}", "{\"data\": null}",
				"{\"data\": {\"id\": \"target\"}}",
				"{\"data\": {\"id\": \"other\", \"name\": \"Other\", \"unit\": \"g\"}}")) {
			stub.fail(200, body);
			assertThrows(RestClientException.class, () -> client.find("target"));
		}
		stub.reset();
	}

}
