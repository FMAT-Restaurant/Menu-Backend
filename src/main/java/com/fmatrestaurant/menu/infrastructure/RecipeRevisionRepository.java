package com.fmatrestaurant.menu.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.fmatrestaurant.menu.domain.RecipeRevision;

import jakarta.persistence.criteria.Predicate;

public interface RecipeRevisionRepository extends JpaRepository<RecipeRevision, UUID>,
		JpaSpecificationExecutor<RecipeRevision> {

	Optional<RecipeRevision> findByRecipeIdAndRevision(UUID recipeId, int revision);

	boolean existsByRecipeIdAndRevision(UUID recipeId, int revision);

	/**
	 * The current revision of every recipe.
	 *
	 * @param q text searched, case-insensitively, in the name and the description; ignored when blank
	 */
	static Specification<RecipeRevision> currentMatching(String q) {
		return (root, _, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("revision"), root.get("recipe").get("currentRevision")));
			predicates.add(cb.isFalse(root.get("recipe").get("deleted")));
			if (q != null && !q.isBlank()) {
				String pattern = "%" + q.strip().toLowerCase(Locale.ROOT)
						.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("name")), pattern, '\\'),
						cb.like(cb.lower(root.get("description")), pattern, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
