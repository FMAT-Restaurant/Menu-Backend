package com.fmatrestaurant.menu.infrastructure.service;

import com.fmatrestaurant.menu.infrastructure.repository.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.repository.RecipeRevisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AvailabilityService {


    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityService.class);
    private final RecipeRepository recipeRepository;
    private final RecipeRevisionRepository recipeRevisionRepository;

    public AvailabilityService(RecipeRepository recipeRepository, RecipeRevisionRepository recipeRevisionRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeRevisionRepository = recipeRevisionRepository;
    }

    public void disableRecipe(UUID recipeId) {
        recipeRevisionRepository.findCurrentByRecipeId(recipeId)
                .ifPresentOrElse(revision -> {
                    revision.disable();
                    recipeRevisionRepository.save(revision);
                },
                () -> LOG.warn("Recipe with id {} not found", recipeId)
        );
    }

    public void enableRecipe(UUID recipeId) {
        recipeRevisionRepository.findCurrentByRecipeId(recipeId)
                .ifPresentOrElse(revision -> {
                    revision.enable();
                    recipeRevisionRepository.save(revision);
                },
                () -> LOG.warn("Recipe with id {} not found", recipeId)
        );
    }

    public void disableInventoryItem(String inventoryItemId) {
        // Logic to disable the inventory item
    }

    public void enableInventoryItem(String inventoryItemId) {
        // Logic to enable the inventory item
    }
}
