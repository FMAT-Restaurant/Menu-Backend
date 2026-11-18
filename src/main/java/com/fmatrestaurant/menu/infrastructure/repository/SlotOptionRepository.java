package com.fmatrestaurant.menu.infrastructure.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.fmatrestaurant.menu.domain.model.SlotOption;

public interface SlotOptionRepository extends Repository<SlotOption, UUID> {

	/** Sets the availability of an {@code INVENTORY_ITEM} option; 0 when there is no such option. */
	@Modifying
	@Query("update SlotOption o set o.sourceAvailable = :available where o.id = :id "
			+ "and o.sourceType = com.fmatrestaurant.menu.domain.model.SourceType.INVENTORY_ITEM")
	int updateSourceAvailability(@Param("id") UUID id, @Param("available") boolean available);

	/** Whether the {@code INVENTORY_ITEM} option is available as Inventory communicated it; null when unknown. */
	@Query("select o.sourceAvailable from SlotOption o where o.id = :id")
	Boolean findSourceAvailability(@Param("id") UUID id);

}
