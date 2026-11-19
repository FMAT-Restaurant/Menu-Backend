package com.fmatrestaurant.menu.infrastructure;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fmatrestaurant.menu.infrastructure.entity.ProcessedEvent;
import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;

/** Deletes the ids of inbound events older than the retention. Every instance runs it. */
@Component
public class ProcessedEventPurge {

	private final ProcessedEventRepository events;

	public ProcessedEventPurge(ProcessedEventRepository events) {
		this.events = events;
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
	public int purge() {
		return events.deleteProcessedBefore(Instant.now().minus(ProcessedEvent.RETENTION));
	}

}
