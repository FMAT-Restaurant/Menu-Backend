package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fmatrestaurant.menu.application.service.InventoryOptionEvents;
import com.fmatrestaurant.menu.domain.event.InventoryItemDto;
import com.fmatrestaurant.menu.domain.event.SourceEvent;
import com.fmatrestaurant.menu.domain.model.CompositionSlot;
import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.fmatrestaurant.menu.domain.model.OfferStatus;
import com.fmatrestaurant.menu.domain.model.SlotOption;

class InventoryOptionEventsTest {

	private final EventPublisher publisher = mock(EventPublisher.class);

	private static SlotOption inventoryOption(String item, String quantity) {
		return new SlotOption("Option", OfferStatus.ACTIVE, new InventoryItem(item, "Name", "g"),
				new BigDecimal(quantity), "g");
	}

	private static CompositionSlot slot(SlotOption... options) {
		return new CompositionSlot("Slot", BigDecimal.ONE, null, List.of(options));
	}

	private List<SourceEvent> published(int times) {
		ArgumentCaptor<SourceEvent> captor = ArgumentCaptor.forClass(SourceEvent.class);
		verify(publisher, org.mockito.Mockito.times(times)).publish(captor.capture());
		return captor.getAllValues();
	}

	@Test
	void aNewInventoryOptionIsAnnouncedAsCreatedWithItsArticleAndNoName() {
		SlotOption option = inventoryOption("INV-1", "2");

		InventoryOptionEvents.publishChanges(publisher, Map.of(),
				InventoryOptionEvents.snapshot(List.of(slot(option))));

		SourceEvent event = published(1).getFirst();
		assertEquals(SourceEvent.Type.CREATED, event.type());
		assertEquals(option.getId().toString(), event.sourceId());
		assertEquals(null, event.name());
		assertEquals(List.of(new InventoryItemDto("INV-1", new BigDecimal("2"))), event.items());
	}

	@Test
	void anOptionWhoseArticleOrQuantityChangedIsAnnouncedAsUpdated() {
		SlotOption option = inventoryOption("INV-1", "2");
		var before = InventoryOptionEvents.snapshot(List.of(slot(option)));
		option.update(null, null, new InventoryItem("INV-2", "Other", "g"), new BigDecimal("3"), "g");

		InventoryOptionEvents.publishChanges(publisher, before, InventoryOptionEvents.snapshot(List.of(slot(option))));

		SourceEvent event = published(1).getFirst();
		assertEquals(SourceEvent.Type.UPDATED, event.type());
		assertEquals(List.of(new InventoryItemDto("INV-2", new BigDecimal("3"))), event.items());
	}

	@Test
	void anUnchangedOptionOrOneChangedOnlyInScaleIsNotAnnounced() {
		SlotOption option = inventoryOption("INV-1", "2");
		var before = InventoryOptionEvents.snapshot(List.of(slot(option)));
		option.update("Renamed", OfferStatus.INACTIVE, null, new BigDecimal("2.00"), null);

		InventoryOptionEvents.publishChanges(publisher, before, InventoryOptionEvents.snapshot(List.of(slot(option))));

		verify(publisher, never()).publish(any());
	}

	@Test
	void aRemovedOptionIsAnnouncedAsDeletedOnlyWithItsId() {
		SlotOption option = inventoryOption("INV-1", "2");

		InventoryOptionEvents.publishChanges(publisher, InventoryOptionEvents.snapshot(List.of(slot(option))),
				Map.of());

		SourceEvent event = published(1).getFirst();
		assertEquals(SourceEvent.Type.DELETED, event.type());
		assertEquals(option.getId().toString(), event.sourceId());
		assertEquals(List.of(), event.items());
	}

	@Test
	void recipeOptionsAreNotSourcesOfInventory() {
		SlotOption recipeOption = new SlotOption("Recipe", OfferStatus.ACTIVE, UUID.randomUUID(), 1);

		assertEquals(Map.of(), InventoryOptionEvents.snapshot(List.of(slot(recipeOption))));
	}
}
