package com.fmatrestaurant.menu.infrastructure;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An Idempotency-Key bound to the fingerprint of its request. Once the operation succeeded it also keeps
 * the response, which is replayed for 24 hours from that first success; replays do not extend the window.
 * Without a response it only binds the key after a failure that changed nothing, for 24 hours from then.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

	public static final Duration WINDOW = Duration.ofHours(24);

	/** SHA-256 of subject, menu, method, path and key. */
	@Id
	@Column(length = 64)
	private String id;

	@Column(nullable = false, length = 64)
	private String fingerprint;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "response_status")
	private Integer responseStatus;

	@Column(name = "response_content_type")
	private String responseContentType;

	@Column(name = "response_etag")
	private String responseEtag;

	@Column(name = "response_body")
	private byte[] responseBody;

	protected IdempotencyRecord() {
		// Required by JPA.
	}

	public IdempotencyRecord(String id, String fingerprint, Instant now) {
		this.id = id;
		this.fingerprint = fingerprint;
		this.createdAt = now;
	}

	public IdempotencyRecord complete(int status, String contentType, String etag, byte[] body, Instant now) {
		this.responseStatus = status;
		this.responseContentType = contentType;
		this.responseEtag = etag;
		this.responseBody = body;
		this.completedAt = now;
		return this;
	}

	public boolean expired(Instant now) {
		Instant start = completedAt != null ? completedAt : createdAt;
		return !now.isBefore(start.plus(WINDOW));
	}

	public boolean completed() {
		return completedAt != null;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public int getResponseStatus() {
		return responseStatus;
	}

	public String getResponseContentType() {
		return responseContentType;
	}

	public String getResponseEtag() {
		return responseEtag;
	}

	public byte[] getResponseBody() {
		return responseBody;
	}

}
