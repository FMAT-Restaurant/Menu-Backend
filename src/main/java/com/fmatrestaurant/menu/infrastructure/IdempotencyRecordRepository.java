package com.fmatrestaurant.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The locks are PostgreSQL advisory locks of the current transaction: they are released when it ends,
 * also if the instance dies, so no operation stays "in progress" forever.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

	/** False while another transaction runs an operation with the same key. */
	@Query(value = "select pg_try_advisory_xact_lock(hashtextextended(:id, 0))", nativeQuery = true)
	boolean tryLock(@Param("id") String id);

	/** Waits for the transaction that runs an operation with the same key. */
	@Query(value = "select true from pg_advisory_xact_lock(hashtextextended(:id, 0))", nativeQuery = true)
	boolean lock(@Param("id") String id);

}
