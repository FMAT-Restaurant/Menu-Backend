package com.fmatrestaurant.menu.infrastructure;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes the expired idempotency records, which are no longer replayed nor bind their key. Every instance
 * runs it; concurrent runs just delete the same rows.
 */
@Component
public class IdempotencyRecordPurge {

	private final IdempotencyRecordRepository records;

	public IdempotencyRecordPurge(IdempotencyRecordRepository records) {
		this.records = records;
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
	public int purge() {
		return records.deleteStartedBefore(Instant.now().minus(IdempotencyRecord.WINDOW));
	}

}
