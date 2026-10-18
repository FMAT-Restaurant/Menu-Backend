package com.fmatrestaurant.menu.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.fmatrestaurant.menu.domain.CatalogOfferRevision;

/** Read-only access to history, including after deletion of the live offer or entry. */
public interface CatalogOfferRevisionRepository extends Repository<CatalogOfferRevision, UUID> {

	Optional<CatalogOfferRevision> findByOfferIdAndRevision(UUID offerId, long revision);

	List<CatalogOfferRevision> findByOfferIdOrderByRevision(UUID offerId);
}
