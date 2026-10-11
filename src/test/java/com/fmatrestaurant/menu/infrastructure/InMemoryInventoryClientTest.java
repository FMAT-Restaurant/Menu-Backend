package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fmatrestaurant.menu.infrastructure.InventoryClient.InventoryItemPage;

class InMemoryInventoryClientTest {

	private final InMemoryInventoryClient client = new InMemoryInventoryClient();

	@Test
	void findsTheFixedArticlesOnly() {
		UUID papas = InMemoryInventoryClient.ITEMS.get(0).id();

		assertEquals("Papas", client.find(papas).orElseThrow().name());
		assertTrue(client.find(UUID.randomUUID()).isEmpty());
	}

	@Test
	void searchesByNameAndPaginates() {
		assertEquals(1, client.search(" CHEDDAR ", 1, 12).data().size());
		assertEquals(InMemoryInventoryClient.ITEMS.size(), client.search(null, 1, 12).meta().total());

		InventoryItemPage second = client.search(null, 2, 4);
		assertEquals(2, second.data().size());
		assertEquals(2, second.meta().totalPages());
	}

}
