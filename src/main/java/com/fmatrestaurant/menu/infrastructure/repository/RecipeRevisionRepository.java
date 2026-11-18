package com.fmatrestaurant.menu.infrastructure.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.fmatrestaurant.menu.domain.model.RecipeRevision;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRevisionRepository extends JpaRepository<RecipeRevision, UUID>,
		JpaSpecificationExecutor<RecipeRevision> {

	Optional<RecipeRevision> findByRecipeIdAndRevision(UUID recipeId, int revision);

	boolean existsByRecipeIdAndRevision(UUID recipeId, int revision);

	@Query(
		"""
		SELECT r FROM RecipeRevision r
		WHERE r.recipe.id = :recipeId AND r.revision = r.recipe.currentRevision
		"""
	)
	Optional<RecipeRevision> findCurrentByRecipeId(UUID recipeId);

	/**
	 * Pairs of recipe id and the availability Inventory communicated for it, read from the current revision; every
	 * revision of a recipe holds the same value.
	 */
	@Query("select r.recipe.id, r.isAvailable from RecipeRevision r "
			+ "where r.recipe.id in :recipeIds and r.revision = r.recipe.currentRevision")
	List<Object[]> findAvailabilityByRecipeIds(@Param("recipeIds") Collection<UUID> recipeIds);

	/**
	 * Sets the availability that Inventory communicated for the recipe on every revision, since the content source
	 * is the recipe; 0 when there is no such recipe.
	 */
	@Modifying
	@Query("update RecipeRevision r set r.isAvailable = :available where r.recipe.id = :recipeId")
	int updateAvailability(@Param("recipeId") UUID recipeId, @Param("available") boolean available);

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
