package com.fmatrestaurant.menu.infrastructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.fmatrestaurant.menu.domain.CatalogOffer;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;

public interface CatalogOfferRepository extends JpaRepository<CatalogOffer, UUID>,
		JpaSpecificationExecutor<CatalogOffer> {

	/**
	 * Loads the offer for an update. Its version increases on commit even if only its slots or
	 * options change, so the ETag reflects every change of the composition.
	 */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	Optional<CatalogOffer> findWithLockById(UUID id);

	/** Pairs of entry id and number of offers, only for entries that have offers. */
	@Query("select o.entry.id, count(o) from CatalogOffer o where o.entry.id in :entryIds group by o.entry.id")
	List<Object[]> countByEntryIds(Collection<UUID> entryIds);

	/**
	 * @param entryId only offers of this entry
	 * @param excludeOfferId leaves this offer out
	 */
	static Specification<CatalogOffer> search(UUID entryId, UUID excludeOfferId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (entryId != null) {
				predicates.add(cb.equal(root.get("entry").get("id"), entryId));
			}
			if (excludeOfferId != null) {
				predicates.add(cb.notEqual(root.get("id"), excludeOfferId));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
