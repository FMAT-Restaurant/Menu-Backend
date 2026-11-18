package com.fmatrestaurant.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fmatrestaurant.menu.application.service.AvailabilityService;
import com.fmatrestaurant.menu.application.service.AvailabilityService.Outcome;
import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;
import com.fmatrestaurant.menu.infrastructure.repository.SlotOptionRepository;

class AvailabilityServiceTest {

	private static final UUID SOURCE = UUID.randomUUID();

	private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);

	private final RecipeRevisionRepository revisions = mock(RecipeRevisionRepository.class);

	private final SlotOptionRepository options = mock(SlotOptionRepository.class);

	private final AvailabilityService service = new AvailabilityService(processedEvents, revisions, options);

	@Test
	@DisplayName("Un evento ya procesado no busca el origen")
	void aDuplicateChangesNothing() {
		when(processedEvents.markProcessed(eq("e-1"), any())).thenReturn(0);

		assertEquals(Outcome.DUPLICATE, service.apply("e-1", SOURCE, true));
		verify(revisions, never()).updateAvailability(any(), anyBoolean());
		verify(options, never()).updateSourceAvailability(any(), anyBoolean());
	}

	@Test
	@DisplayName("Una receta se actualiza antes de buscar una opción")
	void aRecipeIsTriedFirst() {
		when(processedEvents.markProcessed(eq("e-1"), any())).thenReturn(1);
		when(revisions.updateAvailability(SOURCE, false)).thenReturn(2);

		assertEquals(Outcome.RECIPE, service.apply("e-1", SOURCE, false));
		verify(options, never()).updateSourceAvailability(any(), anyBoolean());
	}

	@Test
	@DisplayName("Sin receta se actualiza la opción INVENTORY_ITEM; sin ninguna, el origen es desconocido")
	void anOptionOrAnUnknownSource() {
		when(processedEvents.markProcessed(anyString(), any())).thenReturn(1);
		when(options.updateSourceAvailability(SOURCE, true)).thenReturn(1);

		assertEquals(Outcome.OPTION, service.apply("e-1", SOURCE, true));
		assertEquals(Outcome.UNKNOWN_SOURCE, service.apply("e-2", UUID.randomUUID(), true));
	}

	@Test
	@DisplayName("Sin id de evento se aplica sin registrarlo")
	void withoutAnIdTheEventIsApplied() {
		when(options.updateSourceAvailability(SOURCE, true)).thenReturn(1);

		assertEquals(Outcome.OPTION, service.apply(null, SOURCE, true));
		assertEquals(Outcome.OPTION, service.apply(" ", SOURCE, true));
		verify(processedEvents, never()).markProcessed(any(), any());
	}

}
