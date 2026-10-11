package com.fmatrestaurant.menu.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.InventoryClient.InventoryItemPage;

/**
 * Search of Inventory articles to use as content of slot options (REQ-MENU-CONT-002). Inventory
 * owns them; Menu only reads them (BR-MENU-018).
 */
@Service
public class InventoryItemService {

	private final InventoryClient inventoryClient;

	public InventoryItemService(InventoryClient inventoryClient) {
		this.inventoryClient = inventoryClient;
	}

	/**
	 * @throws InvalidFieldException if the page or the page size are not valid
	 * @throws InventoryUnavailableException if Inventory cannot answer
	 */
	public Page<InventoryItem> search(String q, int page, int pageSize) {
		Pages.check(page, pageSize);
		try {
			InventoryItemPage items = inventoryClient.search(q, page, pageSize);
			return new PageImpl<>(items.data(), PageRequest.of(page - 1, pageSize), items.meta().total());
		} catch (RestClientException e) {
			throw new InventoryUnavailableException(e);
		}
	}

	/**
	 * @return empty when Inventory does not know the article
	 * @throws InventoryUnavailableException if Inventory cannot answer
	 */
	public Optional<InventoryItem> find(UUID id) {
		try {
			return inventoryClient.find(id);
		} catch (RestClientException e) {
			throw new InventoryUnavailableException(e);
		}
	}

}
