package com.fmatrestaurant.menu.infrastructure;

import com.fmatrestaurant.menu.domain.CatalogOffer;

/** Repository fragment that appends immutable, independently numbered offer history. */
public interface CatalogOfferHistory {

	void recordRevision(CatalogOffer offer);
}
