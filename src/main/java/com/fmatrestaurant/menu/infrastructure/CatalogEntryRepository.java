package com.fmatrestaurant.menu.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.EntryStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, UUID>,
		JpaSpecificationExecutor<CatalogEntry> {

	/**
	 * Administrative search of the entries of a menu.
	 *
	 * @param q text searched, case-insensitively, in the brand name, the description and the category names
	 * @param categoryId only entries classified in this category
	 * @param uncategorized only entries without categories
	 * @param status only entries with this status; when {@code null}, archived entries are left out
	 */
	static Specification<CatalogEntry> search(Long menuId, String q, UUID categoryId, boolean uncategorized,
			EntryStatus status) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("menuId"), menuId));
			predicates.add(status == null
					? cb.notEqual(root.get("status"), EntryStatus.ARCHIVED)
					: cb.equal(root.get("status"), status));
			if (uncategorized) {
				predicates.add(cb.isEmpty(root.get("categories")));
			}
			if (categoryId != null) {
				predicates.add(cb.equal(root.join("categories").get("id"), categoryId));
			}
			if (q != null && !q.isBlank()) {
				String pattern = "%" + q.strip().toLowerCase(Locale.ROOT)
						.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				Join<CatalogEntry, Category> category = root.join("categories", JoinType.LEFT);
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("brandName")), pattern, '\\'),
						cb.like(cb.lower(root.get("description")), pattern, '\\'),
						cb.like(cb.lower(category.get("name")), pattern, '\\')));
				query.distinct(true);
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
