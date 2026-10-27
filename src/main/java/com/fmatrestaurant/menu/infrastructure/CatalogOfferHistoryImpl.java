package com.fmatrestaurant.menu.infrastructure;

import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.CatalogOfferRevision;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Appends in the caller's transaction; optimistic locking also protects concurrent publication. */
public class CatalogOfferHistoryImpl implements CatalogOfferHistory {

	private final EntityManager entityManager;

	public CatalogOfferHistoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public void recordRevision(CatalogOffer offer) {
		long lastRevision = entityManager.createQuery(
				"select coalesce(max(r.revision), 0) from CatalogOfferRevision r where r.offerId = :offerId", Long.class)
				.setParameter("offerId", offer.getId()).getSingleResult();
		entityManager.persist(new CatalogOfferRevision(offer, lastRevision + 1));
	}

	@Override
	public void incrementVersion(CatalogOffer offer) {
		entityManager.lock(offer, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
	}
}
