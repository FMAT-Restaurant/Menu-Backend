package com.fmatrestaurant.menu.infrastructure;

import com.fmatrestaurant.menu.domain.CatalogOffer;

/** Repository fragment that appends immutable, independently numbered offer history. */
public interface CatalogOfferHistory {

	void recordRevision(CatalogOffer offer);

	/**
	 * Increments the version right away, even if only its slots or options changed. The response is built
	 * before the transaction commits, so an increment on commit would leave it with a stale ETag.
	 */
	void incrementVersion(CatalogOffer offer);
}
