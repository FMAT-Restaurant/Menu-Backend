package com.fmatrestaurant.menu.infrastructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.Category;
import com.fmatrestaurant.menu.domain.EntryStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, UUID>,
		JpaSpecificationExecutor<CatalogEntry> {

	/** Counts distinct entries in the menu for each requested category. */
	@Query("""
			select category.id as categoryId, count(distinct entry.id) as entryCount
			from CatalogEntry entry join entry.categories category
			where entry.menuId = :menuId and category.id in :categoryIds
			group by category.id
			""")
	List<CategoryEntryCount> countDistinctEntriesByCategory(@Param("menuId") Long menuId,
			@Param("categoryIds") Collection<UUID> categoryIds);

	interface CategoryEntryCount {
		UUID getCategoryId();

		Long getEntryCount();
	}

	/** Removes the join-table links for a category without deleting its catalog entries. */
	@Modifying(flushAutomatically = true)
	@Query(value = "delete from catalog_entry_category where category_id = :categoryId", nativeQuery = true)
	void deleteCategoryAssociations(@Param("categoryId") UUID categoryId);

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
		String categories = "categories";
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("menuId"), menuId));
			predicates.add(status == null
					? cb.notEqual(root.get("status"), EntryStatus.ARCHIVED)
					: cb.equal(root.get("status"), status));
			if (uncategorized) {
				predicates.add(cb.isEmpty(root.get(categories)));
			}
			if (categoryId != null) {
				predicates.add(cb.equal(root.join(categories).get("id"), categoryId));
			}
			if (q != null && !q.isBlank()) {
				String pattern = "%" + q.strip().toLowerCase(Locale.ROOT)
						.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				Join<CatalogEntry, Category> category = root.join(categories, JoinType.LEFT);
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
