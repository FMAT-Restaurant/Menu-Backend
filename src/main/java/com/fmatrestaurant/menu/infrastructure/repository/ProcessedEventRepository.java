package com.fmatrestaurant.menu.infrastructure.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.infrastructure.entity.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

	/** Records the event; 0 when it was already recorded, also by a concurrent transaction. */
	@Modifying
	@Query(value = "insert into processed_event (event_id, processed_at) values (:eventId, :now) "
			+ "on conflict (event_id) do nothing", nativeQuery = true)
	int markProcessed(@Param("eventId") String eventId, @Param("now") Instant now);

	@Transactional
	@Modifying
	@Query("delete from ProcessedEvent e where e.processedAt < :cutoff")
	int deleteProcessedBefore(@Param("cutoff") Instant cutoff);

}
