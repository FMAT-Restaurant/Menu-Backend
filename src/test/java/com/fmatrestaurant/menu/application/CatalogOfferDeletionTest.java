package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;

class CatalogOfferDeletionTest {

	private static final UUID ENTRY_ID = UUID.randomUUID();

	private static final UUID OFFER_ID = UUID.randomUUID();

	private static final long VERSION = 5;

	private CatalogEntryRepository entryRepository;

	private CatalogOfferRepository offerRepository;

	private CatalogOfferService service;

	@BeforeEach
	void setUp() {
		entryRepository = mock(CatalogEntryRepository.class);
		offerRepository = mock(CatalogOfferRepository.class);
		service = new CatalogOfferService(offerRepository, entryRepository, mock(ImageRepository.class),
				mock(InventoryClient.class), mock(RecipeRevisionRepository.class), mock(RecipeRepository.class));
	}

	@Test
	void deletesAnInactiveOfferOfAnInactiveEntry() {
		CatalogEntry entry = entry(EntryStatus.INACTIVE);
		CatalogOffer offer = loaded(entry, false);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(offer));

		service.delete(OFFER_ID, VERSION);

		verify(offerRepository).delete(offer);
	}

	@Test
	void deletesAnInactiveOfferOfAnArchivedEntryWithoutOtherOffers() {
		CatalogEntry entry = entry(EntryStatus.ARCHIVED);
		CatalogOffer offer = loaded(entry, false);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(offer));

		service.delete(OFFER_ID, VERSION);

		verify(offerRepository).delete(offer);
	}

	@Test
	void deletesAnInactiveOfferOfAnActiveEntryThatKeepsAnotherActiveOffer() {
		CatalogEntry entry = entry(EntryStatus.ACTIVE);
		CatalogOffer offer = loaded(entry, false);
		CatalogOffer other = offer(entry, UUID.randomUUID(), true);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(offer, other));
		when(offerRepository.incrementVersion(other.getId(), VERSION)).thenReturn(1);

		service.delete(OFFER_ID, VERSION);

		verify(offerRepository).incrementVersion(other.getId(), VERSION);
		verify(offerRepository).delete(offer);
	}

	@Test
	void rejectsWhenTheOtherActiveOfferChangedWhileItWasChecked() {
		CatalogEntry entry = entry(EntryStatus.ACTIVE);
		CatalogOffer offer = loaded(entry, false);
		CatalogOffer other = offer(entry, UUID.randomUUID(), true);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(offer, other));
		when(offerRepository.incrementVersion(other.getId(), VERSION)).thenReturn(0);

		DeletionNotAllowedException exception = assertThrows(DeletionNotAllowedException.class,
				() -> service.delete(OFFER_ID, VERSION));

		assertEquals("The offers of the entry changed while the deletion was checked; try again", exception.getMessage());
		verify(offerRepository, never()).delete(any(CatalogOffer.class));
	}

	@Test
	void rejectsAnActiveOffer() {
		CatalogEntry entry = entry(EntryStatus.INACTIVE);
		loaded(entry, true);

		DeletionNotAllowedException exception = assertThrows(DeletionNotAllowedException.class,
				() -> service.delete(OFFER_ID, VERSION));

		assertEquals("Only an INACTIVE offer can be deleted", exception.getMessage());
		verify(offerRepository, never()).delete(any(CatalogOffer.class));
	}

	@Test
	void rejectsLeavingAnActiveEntryWithoutAnotherActiveOffer() {
		CatalogEntry entry = entry(EntryStatus.ACTIVE);
		CatalogOffer offer = loaded(entry, false);
		CatalogOffer inactiveOther = offer(entry, UUID.randomUUID(), false);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(offer, inactiveOther));

		assertThrows(DeletionNotAllowedException.class, () -> service.delete(OFFER_ID, VERSION));

		verify(offerRepository, never()).delete(any(CatalogOffer.class));
	}

	@Test
	void rejectsLeavingAnActiveEntryWithoutOffers() {
		CatalogEntry entry = entry(EntryStatus.ACTIVE);
		CatalogOffer offer = loaded(entry, false);
		when(offerRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(offer));

		assertThrows(DeletionNotAllowedException.class, () -> service.delete(OFFER_ID, VERSION));

		verify(offerRepository, never()).delete(any(CatalogOffer.class));
	}

	@Test
	void rejectsAStaleVersionWithoutDeletingAnything() {
		CatalogEntry entry = entry(EntryStatus.INACTIVE);
		loaded(entry, false);

		assertThrows(StaleCatalogOfferException.class, () -> service.delete(OFFER_ID, VERSION + 1));

		verify(offerRepository, never()).delete(any(CatalogOffer.class));
	}

	@Test
	void checksTheVersionBeforeTheStatus() {
		CatalogEntry entry = entry(EntryStatus.INACTIVE);
		loaded(entry, true);

		assertThrows(StaleCatalogOfferException.class, () -> service.delete(OFFER_ID, VERSION + 1));
	}

	@Test
	void failsWhenTheOfferDoesNotExist() {
		when(offerRepository.findEntryIdById(OFFER_ID)).thenReturn(Optional.empty());

		assertThrows(CatalogOfferNotFoundException.class, () -> service.delete(OFFER_ID, VERSION));

		verify(offerRepository, never()).delete(any(CatalogOffer.class));
	}

	@Test
	void failsWhenTheOfferWasDeletedWhileWaitingForTheLock() {
		CatalogEntry entry = entry(EntryStatus.INACTIVE);
		when(offerRepository.findEntryIdById(OFFER_ID)).thenReturn(Optional.of(entry.getId()));
		when(offerRepository.findForDeletionById(OFFER_ID)).thenReturn(Optional.empty());

		assertThrows(CatalogOfferNotFoundException.class, () -> service.delete(OFFER_ID, VERSION));
	}

	private CatalogEntry entry(EntryStatus status) {
		CatalogEntry entry = new CatalogEntry(1L, "Hawaiian burger", "Tasty", UUID.randomUUID(), List.of());
		entry.update(null, null, null, null, status);
		ReflectionTestUtils.setField(entry, "id", ENTRY_ID);
		when(entryRepository.findWithLockById(ENTRY_ID)).thenReturn(Optional.of(entry));
		return entry;
	}

	/** An offer as the repository returns it for the deletion, with the version JPA assigns. */
	private CatalogOffer loaded(CatalogEntry entry, boolean active) {
		CatalogOffer offer = offer(entry, CatalogOfferDeletionTest.OFFER_ID, active);
		when(offerRepository.findEntryIdById(CatalogOfferDeletionTest.OFFER_ID)).thenReturn(Optional.of(entry.getId()));
		when(offerRepository.findForDeletionById(CatalogOfferDeletionTest.OFFER_ID)).thenReturn(Optional.of(offer));
		return offer;
	}

	private static CatalogOffer offer(CatalogEntry entry, UUID id, boolean active) {
		SlotOption option = new SlotOption("Fries", OfferStatus.ACTIVE,
				new InventoryItem("INV-" + UUID.randomUUID(), "Potatoes", "g"), new BigDecimal("200"), "g");
		CompositionSlot slot = new CompositionSlot("Side", BigDecimal.ONE, null, List.of(option));
		CatalogOffer offer = new CatalogOffer(entry, "Individual", BigDecimal.TEN, UUID.randomUUID(), List.of(slot));
		if (active) {
			offer.changeStatus(OfferStatus.ACTIVE);
		}
		ReflectionTestUtils.setField(offer, "id", id);
		ReflectionTestUtils.setField(offer, "version", VERSION);
		return offer;
	}

}
