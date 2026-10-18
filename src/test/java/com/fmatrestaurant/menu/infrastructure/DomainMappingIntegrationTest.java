package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.CompositionSlot;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.domain.SlotOption;

import jakarta.persistence.EntityManager;

/** Verifies the PostgreSQL schema generated from JPA, rather than only inspecting annotations. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class DomainMappingIntegrationTest {

	@Autowired
	private CatalogEntryRepository entries;

	@Autowired
	private CatalogOfferRepository offers;

	@Autowired
	private CatalogOfferRevisionRepository revisions;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private CatalogOffer recipeOffer() {
		CatalogEntry entry = entries.saveAndFlush(new CatalogEntry(1L, "Product", "Description",
				UUID.randomUUID(), List.of()));
		SlotOption option = new SlotOption("Recipe", OfferStatus.ACTIVE, UUID.randomUUID(), 1);
		CompositionSlot slot = new CompositionSlot("Main", BigDecimal.ONE, null, List.of(option));
		return offers.saveAndFlush(new CatalogOffer(entry, "Individual", BigDecimal.TEN, UUID.randomUUID(), List.of(slot)));
	}

	@Test
	void persistsRecipeSourcesWithNullInventoryFieldsAndRequiredPresentation() {
		CatalogOffer offer = recipeOffer();
		UUID id = offer.getId();
		entityManager.clear();
		CatalogOffer loaded = offers.findById(id).orElseThrow();
		assertEquals("Individual", loaded.getPresentationTag());
		SlotOption option = loaded.getSlots().getFirst().getOptions().getFirst();
		assertNotNull(option.getRecipeId());
		assertEquals(1, option.getRecipeRevision());
		assertNull(option.getInventoryItemId());
		assertNull(option.getSourceName());
		assertNull(option.getQuantity());
		assertNull(option.getUnit());
		for (String column : List.of("inventory_item_id", "quantity", "source_name", "unit")) {
			assertEquals("YES", jdbc.queryForObject("select is_nullable from information_schema.columns "
					+ "where table_schema='public' and table_name='slot_option' and column_name=?", String.class, column));
		}
	}

	@Test
	void databaseRejectsMixingRecipeAndInventorySources() {
		CatalogOffer offer = recipeOffer();
		UUID optionId = offer.getSlots().getFirst().getOptions().getFirst().getId();
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
				"update slot_option set inventory_item_id = 'INV-1' where id = ?", optionId));
	}

	@Test
	void historicalCompositionSurvivesChangesAndDeletionOfItsLiveOwners() {
		CatalogOffer offer = recipeOffer();
		UUID offerId = offer.getId();
		CatalogEntry entry = offer.getEntry();
		offers.recordRevision(offer);
		entityManager.flush();
		offer.update("Family", BigDecimal.valueOf(20), null);
		offer.getSlots().getFirst().getOptions().getFirst().updateRecipe(null, OfferStatus.INACTIVE, null, null);
		offers.saveAndFlush(offer);
		offers.recordRevision(offer);
		entityManager.flush();
		offers.delete(offer);
		offers.flush();
		entries.delete(entry);
		entries.flush();
		entityManager.clear();

		var first = revisions.findByOfferIdAndRevision(offerId, 1).orElseThrow();
		var second = revisions.findByOfferIdAndRevision(offerId, 2).orElseThrow();
		assertEquals("Product", first.getBrandNameSnapshot());
		assertEquals(0, BigDecimal.TEN.compareTo(first.getBasePrice()));
		assertEquals("Individual", first.getPresentationTag());
		assertEquals("Family", second.getPresentationTag());
		assertEquals(OfferStatus.ACTIVE, first.getCompositionSnapshot().slots().getFirst().status());
		assertEquals(OfferStatus.INACTIVE, second.getCompositionSnapshot().slots().getFirst().status());
		assertEquals(2, revisions.findByOfferIdOrderByRevision(offerId).size());
		assertThrows(UnsupportedOperationException.class, () -> first.getCompositionSnapshot().slots().clear());
	}
}
