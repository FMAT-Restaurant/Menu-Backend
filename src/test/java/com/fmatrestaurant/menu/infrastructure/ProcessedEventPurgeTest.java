package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;

class ProcessedEventPurgeTest {

	@Test
	@DisplayName("Borra los ids de eventos con más de 7 días")
	void deletesTheIdsOlderThanTheRetention() {
		ProcessedEventRepository events = mock(ProcessedEventRepository.class);
		when(events.deleteProcessedBefore(any())).thenReturn(3);

		assertEquals(3, new ProcessedEventPurge(events).purge());

		ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
		verify(events).deleteProcessedBefore(cutoff.capture());
		Duration age = Duration.between(cutoff.getValue(), Instant.now());
		assertTrue(age.compareTo(Duration.ofDays(7)) >= 0 && age.compareTo(Duration.ofDays(7).plusMinutes(1)) < 0,
				() -> "cutoff " + age);
	}

}
