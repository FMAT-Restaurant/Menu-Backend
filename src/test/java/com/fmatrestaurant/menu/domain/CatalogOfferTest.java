package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CatalogOfferTest {

	private static final InventoryItem POTATOES = new InventoryItem(UUID.randomUUID(), "Potatoes", "g");

	private final CatalogEntry entry = new CatalogEntry(1L, "Burger", "Tasty", UUID.randomUUID(), List.of());

	@Test
	void startsInactiveWithItsOwnTagPriceImageAndComposition() {
		UUID imageId = UUID.randomUUID();
		CatalogOffer offer = new CatalogOffer(entry, "  Combo ", new BigDecimal("180.50"), imageId,
				List.of(slot(OfferStatus.ACTIVE)));

		assertEquals(OfferStatus.INACTIVE, offer.getStatus());
		assertEquals("Combo", offer.getPresentationTag());
		assertEquals(new BigDecimal("180.50"), offer.getBasePrice());
		assertEquals(imageId, offer.getImageId());
		assertEquals(entry, offer.getEntry());
		assertNull(new CatalogOffer(entry, " ", BigDecimal.ZERO, imageId, List.of(slot(OfferStatus.ACTIVE)))
				.getPresentationTag());
	}

	@Test
	void rejectsAnInvalidOffer() {
		UUID imageId = UUID.randomUUID();
		List<CompositionSlot> slots = List.of(slot(OfferStatus.ACTIVE));

		assertPath("/basePrice", () -> new CatalogOffer(entry, null, null, imageId, slots));
		assertPath("/basePrice", () -> new CatalogOffer(entry, null, new BigDecimal("-1"), imageId, slots));
		assertPath("/imageId", () -> new CatalogOffer(entry, null, BigDecimal.ONE, null, slots));
		assertPath("/entryId", () -> new CatalogOffer(null, null, BigDecimal.ONE, imageId, slots));
		assertPath("/composition/slots", () -> new CatalogOffer(entry, null, BigDecimal.ONE, imageId, List.of()));
		assertPath("/presentationTag", () -> new CatalogOffer(entry, "x".repeat(256), BigDecimal.ONE, imageId, slots));
	}

	@Test
	void slotStatusIsDerivedFromItsOptions() {
		CompositionSlot slot = new CompositionSlot("Side", BigDecimal.ONE, null,
				List.of(option(OfferStatus.INACTIVE), option(OfferStatus.INACTIVE)));
		assertEquals(OfferStatus.INACTIVE, slot.getStatus());

		slot.replaceOptions(List.of(option(OfferStatus.INACTIVE), option(OfferStatus.ACTIVE)));
		assertEquals(OfferStatus.ACTIVE, slot.getStatus());
	}

	@Test
	void activationNeedsAnActiveSlot() {
		CatalogOffer offer = offer(slot(OfferStatus.INACTIVE));

		assertPath("/status", () -> offer.changeStatus(OfferStatus.ACTIVE));
		assertEquals(OfferStatus.INACTIVE, offer.getStatus());

		offer.replaceSlots(List.of(slot(OfferStatus.INACTIVE), slot(OfferStatus.ACTIVE)));
		offer.changeStatus(OfferStatus.ACTIVE);
		assertEquals(OfferStatus.ACTIVE, offer.getStatus());
	}

	@Test
	void losingTheLastActiveSlotInactivatesAndRecoveringItReactivates() {
		CompositionSlot slot = slot(OfferStatus.ACTIVE);
		CatalogOffer offer = offer(slot);
		offer.changeStatus(OfferStatus.ACTIVE);

		slot.replaceOptions(List.of(option(OfferStatus.INACTIVE)));
		assertEquals(OfferStatus.INACTIVE, offer.getStatus());

		slot.replaceOptions(List.of(option(OfferStatus.ACTIVE)));
		assertEquals(OfferStatus.ACTIVE, offer.getStatus());
	}

	@Test
	void anExplicitInactivationPrevailsOverTheReactivation() {
		CompositionSlot slot = slot(OfferStatus.ACTIVE);
		CatalogOffer offer = offer(slot);
		offer.changeStatus(OfferStatus.ACTIVE);
		slot.replaceOptions(List.of(option(OfferStatus.INACTIVE)));
		offer.changeStatus(OfferStatus.INACTIVE);

		slot.replaceOptions(List.of(option(OfferStatus.ACTIVE)));
		assertEquals(OfferStatus.INACTIVE, offer.getStatus());

		offer.changeStatus(OfferStatus.ACTIVE);
		assertEquals(OfferStatus.ACTIVE, offer.getStatus());
	}

	@Test
	void updateKeepsMissingValuesAndRejectsInvalidOnesWithoutChanges() {
		CatalogOffer offer = new CatalogOffer(entry, "Combo", BigDecimal.TEN, UUID.randomUUID(),
				List.of(slot(OfferStatus.ACTIVE)));

		assertPath("/basePrice", () -> offer.update("Family", new BigDecimal("-5"), null));
		assertEquals("Combo", offer.getPresentationTag());

		offer.update(null, null, null);
		assertEquals("Combo", offer.getPresentationTag());
		assertEquals(BigDecimal.TEN, offer.getBasePrice());

		offer.update("", BigDecimal.ONE, null);
		assertNull(offer.getPresentationTag());
		assertEquals(BigDecimal.ONE, offer.getBasePrice());
	}

	@Test
	void slotQuantityIsAPositiveWholeNumberOfRounds() {
		List<SlotOption> options = List.of(option(OfferStatus.ACTIVE));

		assertEquals(3, new CompositionSlot("Side", new BigDecimal("3.0"), null, options).getQuantity());
		assertPath("/quantity", () -> new CompositionSlot("Side", new BigDecimal("1.5"), null, options));
		assertPath("/quantity", () -> new CompositionSlot("Side", BigDecimal.ZERO, null, options));
		assertPath("/quantity", () -> new CompositionSlot("Side", null, null, options));
		assertPath("/name", () -> new CompositionSlot(" ", BigDecimal.ONE, null, options));
		assertPath("/options", () -> new CompositionSlot("Side", BigDecimal.ONE, null, List.of()));
	}

	@Test
	void courseIsOptionalAndOnlyAcceptsTheKnownValues() {
		CompositionSlot slot = new CompositionSlot("Main", BigDecimal.ONE, "plato fuerte",
				List.of(option(OfferStatus.ACTIVE)));
		assertEquals("plato fuerte", slot.getCourse());

		assertPath("/course", () -> slot.update("Other", null, Optional.of("snack")));
		assertEquals("Main", slot.getName());

		slot.update(null, null, null);
		assertEquals("plato fuerte", slot.getCourse());
		slot.update(null, null, Optional.empty());
		assertNull(slot.getCourse());
	}

	@Test
	void inventoryContentNeedsAPositiveQuantityAndTheUnitOfTheArticle() {
		SlotOption option = new SlotOption(" Fries ", OfferStatus.ACTIVE, POTATOES, new BigDecimal("200"), "g");
		assertEquals("Fries", option.getDisplayName());
		assertEquals(SourceType.INVENTORY_ITEM, option.getSourceType());
		assertEquals(POTATOES.id(), option.getInventoryItemId());
		assertEquals("Potatoes", option.getSourceName());

		assertPath("/quantity", () -> new SlotOption("Fries", OfferStatus.ACTIVE, POTATOES, BigDecimal.ZERO, "g"));
		assertPath("/unit", () -> new SlotOption("Fries", OfferStatus.ACTIVE, POTATOES, BigDecimal.ONE, "kg"));
		assertPath("/status", () -> new SlotOption("Fries", null, POTATOES, BigDecimal.ONE, "g"));
		assertPath("/inventoryItemId", () -> new SlotOption("Fries", OfferStatus.ACTIVE, null, BigDecimal.ONE, "g"));
	}

	@Test
	void optionUpdateKeepsMissingValuesAndChecksANewArticle() {
		SlotOption option = new SlotOption("Fries", OfferStatus.ACTIVE, POTATOES, new BigDecimal("200"), "g");
		InventoryItem juice = new InventoryItem(UUID.randomUUID(), "Juice", "ml");

		assertPath("/unit", () -> option.update("Juice", null, juice, null, null));
		assertEquals("Fries", option.getDisplayName());

		option.update(null, OfferStatus.INACTIVE, null, new BigDecimal("250"), null);
		assertEquals(OfferStatus.INACTIVE, option.getStatus());
		assertEquals(new BigDecimal("250"), option.getQuantity());

		option.update("Juice", null, juice, null, "ml");
		assertEquals(juice.id(), option.getInventoryItemId());
		assertEquals("Juice", option.getSourceName());
		assertEquals("ml", option.getUnit());
	}

	@Test
	void prefixedNestsThePath() {
		InvalidFieldException e = new InvalidFieldException("/unit", "Bad unit").prefixed("/composition/slots/0");
		assertEquals("/composition/slots/0/unit", e.getPath());
		assertEquals("Bad unit", e.getMessage());
	}

	private CatalogOffer offer(CompositionSlot slot) {
		return new CatalogOffer(entry, null, BigDecimal.TEN, UUID.randomUUID(), List.of(slot));
	}

	private static CompositionSlot slot(OfferStatus optionStatus) {
		return new CompositionSlot("Side", BigDecimal.ONE, null, List.of(option(optionStatus)));
	}

	private static SlotOption option(OfferStatus status) {
		return new SlotOption("Fries", status, POTATOES, BigDecimal.ONE, "g");
	}

	private static void assertPath(String path, Runnable action) {
		InvalidFieldException e = assertThrows(InvalidFieldException.class, action::run);
		assertEquals(path, e.getPath());
	}

}
