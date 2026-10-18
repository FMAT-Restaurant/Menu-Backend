package com.fmatrestaurant.menu.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A recipe cannot leave the library while any current offer references it. */
public class RecipeInUseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final ArrayList<UUID> offerIds;

	public RecipeInUseException(List<UUID> offerIds) {
		super("The recipe is referenced by current offers.");
		this.offerIds = new ArrayList<>(offerIds);
	}

	public List<UUID> getOfferIds() {
		return List.copyOf(offerIds);
	}

}
