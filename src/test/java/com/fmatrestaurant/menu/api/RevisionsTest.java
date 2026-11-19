package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fmatrestaurant.menu.application.service.DerivedAvailability.OfferAvailability;

class RevisionsTest {

	@Test
	@DisplayName("El ETag con huella conserva la versión que compara If-Match")
	void theFingerprintIsIgnoredByIfMatch() {
		assertEquals("\"rev-3-a\"", Revisions.strong(3, "a"));
		assertEquals(3, Revisions.strongVersion("\"rev-3-a\""));
		assertEquals(3, Revisions.strongVersion("\"rev-3-2986967b\""));
		assertEquals(3, Revisions.strongVersion("\"rev-3\""));
		assertEquals(-1, Revisions.strongVersion("W/\"rev-3-a\""));
		assertEquals(-1, Revisions.strongVersion("\"rev-3-\""));
		assertEquals(-1, Revisions.strongVersion("\"rev-3-A_B\""));
	}

	@Test
	@DisplayName("La huella de una oferta cambia con cualquier valor de isAvailable")
	void theFingerprintFollowsEveryValue() {
		UUID slot = UUID.randomUUID();
		UUID option = UUID.randomUUID();
		String base = availability(true, true, true).fingerprint();

		assertEquals(base, availability(true, true, true).fingerprint());
		assertNotEquals(base, availability(false, true, true).fingerprint());
		assertNotEquals(base, availability(true, false, true).fingerprint());
		assertNotEquals(base, availability(true, true, false).fingerprint());
		assertEquals(base.toLowerCase(), base, "the ETag pattern only accepts lower-case fingerprints");
		assertNotEquals(availability(true, true, true, slot, option).fingerprint(),
				availability(true, true, true, option, slot).fingerprint());
	}

	private static final UUID SLOT = UUID.fromString("00000000-0000-4000-8000-000000000001");

	private static final UUID OPTION = UUID.fromString("00000000-0000-4000-8000-000000000002");

	private static OfferAvailability availability(boolean offer, boolean slot, boolean option) {
		return availability(offer, slot, option, SLOT, OPTION);
	}

	private static OfferAvailability availability(boolean offer, boolean slot, boolean option, UUID slotId,
			UUID optionId) {
		Map<UUID, Boolean> slots = new LinkedHashMap<>();
		slots.put(slotId, slot);
		Map<UUID, Boolean> options = new LinkedHashMap<>();
		options.put(optionId, option);
		return new OfferAvailability(offer, slots, options);
	}

}
