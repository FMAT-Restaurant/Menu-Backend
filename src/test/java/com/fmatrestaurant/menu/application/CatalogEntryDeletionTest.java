package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.CompositionSlot;
import com.fmatrestaurant.menu.domain.EntryStatus;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.domain.SlotOption;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.CategoryRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;

class CatalogEntryDeletionTest {

	private static final UUID ENTRY_ID = UUID.randomUUID();

	private static final long VERSION = 3;

	private CatalogEntryRepository entryRepository;

	private CatalogOfferRepository offerRepository;

	private CatalogEntryService service;

	@BeforeEach
	void setUp() {
		entryRepository = mock(CatalogEntryRepository.class);
		offerRepository = mock(CatalogOfferRepository.class);
		service = new CatalogEntryService(entryRepository, mock(CategoryRepository.class),
				mock(ImageRepository.class), offerRepository);
	}

	@Test
	void deletesAnArchivedEntryAfterItsOffers() {
		CatalogEntry entry = entry(EntryStatus.ARCHIVED);
		List<CatalogOffer> offers = List.of(offer(entry), offer(entry));
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(offers);

		service.delete(ENTRY_ID, VERSION);

		InOrder order = inOrder(offerRepository, entryRepository);
		order.verify(offerRepository).deleteAll(offers);
		order.verify(entryRepository).delete(entry);
	}

	@Test
	void deletesAnArchivedEntryWithoutOffers() {
		CatalogEntry entry = entry(EntryStatus.ARCHIVED);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of());

		service.delete(ENTRY_ID, VERSION);

		verify(entryRepository).delete(entry);
	}

	@Test
	void rejectsAnEntryThatIsNotArchivedWithoutDeletingAnything() {
		for (EntryStatus status : List.of(EntryStatus.ACTIVE, EntryStatus.INACTIVE)) {
			entry(status);

			DeletionNotAllowedException exception = assertThrows(DeletionNotAllowedException.class,
					() -> service.delete(ENTRY_ID, VERSION));

			assertEquals("Only an ARCHIVED catalog entry can be deleted", exception.getMessage());
		}
		verify(offerRepository, never()).deleteAll(any());
		verify(entryRepository, never()).delete(any(CatalogEntry.class));
	}

	@Test
	void rejectsAStaleVersionWithoutDeletingAnything() {
		entry(EntryStatus.ARCHIVED);

		assertThrows(StaleCatalogEntryException.class, () -> service.delete(ENTRY_ID, VERSION + 1));

		verify(offerRepository, never()).deleteAll(any());
		verify(entryRepository, never()).delete(any(CatalogEntry.class));
	}

	@Test
	void checksTheVersionBeforeTheStatus() {
		entry(EntryStatus.ACTIVE);

		assertThrows(StaleCatalogEntryException.class, () -> service.delete(ENTRY_ID, VERSION + 1));
	}

	@Test
	void failsWhenTheEntryDoesNotExist() {
		when(entryRepository.findWithLockById(ENTRY_ID)).thenReturn(Optional.empty());

		assertThrows(CatalogEntryNotFoundException.class, () -> service.delete(ENTRY_ID, VERSION));

		verify(entryRepository, never()).delete(any(CatalogEntry.class));
	}

	/** An entry as loaded by JPA, with the id and the version it assigns. */
	private CatalogEntry entry(EntryStatus status) {
		CatalogEntry entry = new CatalogEntry(1L, "Hawaiian burger", "Tasty", UUID.randomUUID(), List.of());
		entry.update(null, null, null, null, status);
		ReflectionTestUtils.setField(entry, "id", ENTRY_ID);
		ReflectionTestUtils.setField(entry, "version", VERSION);
		when(entryRepository.findWithLockById(ENTRY_ID)).thenReturn(Optional.of(entry));
		return entry;
	}

	private static CatalogOffer offer(CatalogEntry entry) {
		SlotOption option = new SlotOption("Fries", OfferStatus.ACTIVE,
				new InventoryItem(UUID.randomUUID(), "Potatoes", "g"), new BigDecimal("200"), "g");
		CompositionSlot slot = new CompositionSlot("Side", BigDecimal.ONE, null, List.of(option));
		return new CatalogOffer(entry, "Individual", BigDecimal.TEN, UUID.randomUUID(), List.of(slot));
	}

}
