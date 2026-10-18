package com.fmatrestaurant.menu.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fmatrestaurant.menu.domain.Recipe;

import jakarta.persistence.LockModeType;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

	/** Serializes deletion with the creation of references to this recipe. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Recipe r where r.id = :id and r.deleted = false")
	Optional<Recipe> findForDeletionById(@Param("id") UUID id);

	/** Holds the recipe in the library until the offer transaction commits. */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select r from Recipe r where r.id = :id and r.deleted = false")
	Optional<Recipe> findForReferenceById(@Param("id") UUID id);

}
