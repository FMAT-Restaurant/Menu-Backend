package com.fmatrestaurant.menu.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.Recipe;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

}
