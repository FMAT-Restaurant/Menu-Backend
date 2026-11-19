package com.fmatrestaurant.menu.infrastructure.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The id of an inbound event already applied, kept for {@link #RETENTION} to discard repeated deliveries. */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

	public static final Duration RETENTION = Duration.ofDays(7);

	@Id
	@Column(name = "event_id", columnDefinition = "text")
	private String eventId;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedEvent() {
		// Required by JPA.
	}

	public String getEventId() {
		return eventId;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

}
