package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fmatrestaurant.menu.InventoryStub;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.CatalogEntryService;
import com.fmatrestaurant.menu.application.CatalogOfferService;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * HTTP flows to delete entries and offers against PostgreSQL (Testcontainers), including deletions that
 * run concurrently with other writes. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class CatalogDeletionApiIntegrationTest {

	private static final String ENTRIES = "/api/v1/menu/entries";

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final InventoryItem POTATOES = new InventoryItem(UUID.randomUUID(), "Potatoes", "g");

	private static final InventoryStub INVENTORY = InventoryStub.INSTANCE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private CatalogOfferService offerService;

	@Autowired
	private CatalogEntryService entryService;

	private UUID imageId;

	@DynamicPropertySource
	static void inventory(DynamicPropertyRegistry registry) {
		InventoryStub.register(registry);
	}

	@BeforeEach
	void setUp() throws IOException {
		INVENTORY.reset();
		INVENTORY.add(POTATOES);
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		imageRepository.deleteAll();
		imageId = imageRepository.save(new Image("image/png", png())).getId();
	}

	@Test
	void deletesAnArchivedEntryTogetherWithItsOffers() throws Exception {
		UUID entry = newEntry();
		String firstOffer = newOffer(entry);
		String secondOffer = newOffer(entry);
		setEntryStatus(entry, "ARCHIVED");
		INVENTORY.clearRequests();

		deleteEntry(entry, entryEtag(entry)).andExpect(status().isNoContent());

		mockMvc.perform(get(ENTRIES + "/" + entry)).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/" + firstOffer)).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/" + secondOffer)).andExpect(status().isNotFound());
		assertEquals(0, offerRepository.count());
		assertEquals(0, count("composition_slot"));
		assertEquals(0, count("slot_option"));
		// The referenced Inventory articles are not Menu's to delete.
		assertEquals(List.of(), INVENTORY.requests());
	}

	@Test
	void keepsTheOtherEntriesAndTheirOffersWhenDeletingAnEntry() throws Exception {
		UUID deleted = newEntry();
		newOffer(deleted);
		UUID kept = newEntry();
		String keptOffer = newOffer(kept);
		setEntryStatus(deleted, "ARCHIVED");

		deleteEntry(deleted, entryEtag(deleted)).andExpect(status().isNoContent());

		assertTrue(entryRepository.existsById(kept));
		mockMvc.perform(get(OFFERS + "/" + keptOffer)).andExpect(status().isOk());
	}

	@Test
	void rejectsDeletingAnEntryThatIsNotArchived() throws Exception {
		UUID inactive = newEntry();
		String inactiveOffer = newOffer(inactive);
		UUID active = newEntry();
		setEntryStatus(active, "ACTIVE");

		deleteEntry(inactive, entryEtag(inactive)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").exists());
		deleteEntry(active, entryEtag(active)).andExpect(status().isConflict());

		assertTrue(entryRepository.existsById(inactive));
		assertTrue(entryRepository.existsById(active));
		mockMvc.perform(get(OFFERS + "/" + inactiveOffer)).andExpect(status().isOk());
		mockMvc.perform(get(ENTRIES + "/" + inactive)).andExpect(jsonPath("$.data.status").value("INACTIVE"));
		mockMvc.perform(get(ENTRIES + "/" + active)).andExpect(jsonPath("$.data.status").value("ACTIVE"));
		assertEquals(1, count("composition_slot"));
	}

	@Test
	void rejectsDeletingAnEntryWithAStaleWeakOrOutOfRangeEtag() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		setEntryStatus(entry, "ARCHIVED");

		deleteEntry(entry, "\"rev-999\"").andExpect(status().isPreconditionFailed());
		deleteEntry(entry, "W/" + entryEtag(entry)).andExpect(status().isPreconditionFailed());
		deleteEntry(entry, "\"rev-9223372036854775808\"").andExpect(status().isPreconditionFailed());
		deleteEntry(entry, "not an etag").andExpect(status().isPreconditionFailed());

		assertTrue(entryRepository.existsById(entry));
		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(status().isOk());
	}

	@Test
	void requiresIfMatchToDelete() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		setEntryStatus(entry, "ARCHIVED");

		mockMvc.perform(delete(ENTRIES + "/" + entry)).andExpect(status().isPreconditionRequired());
		mockMvc.perform(delete(OFFERS + "/" + offer)).andExpect(status().isPreconditionRequired());

		assertTrue(entryRepository.existsById(entry));
		assertEquals(1, offerRepository.count());
	}

	@Test
	void answersNotFoundWhenTheResourceDoesNotExist() throws Exception {
		deleteEntry(UUID.randomUUID(), "\"rev-0\"").andExpect(status().isNotFound());
		deleteOffer(UUID.randomUUID().toString(), "\"rev-0\"").andExpect(status().isNotFound());
	}

	@Test
	void deletesAnInactiveOfferAndKeepsItsEntryAndTheOtherOffers() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		String other = newOffer(entry);
		assertEquals(2, count("composition_slot"));
		assertEquals(2, count("slot_option"));
		INVENTORY.clearRequests();

		deleteOffer(offer, offerEtag(offer)).andExpect(status().isNoContent());

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/" + other)).andExpect(status().isOk());
		assertTrue(entryRepository.existsById(entry));
		assertEquals(1, count("composition_slot"));
		assertEquals(1, count("slot_option"));
		assertEquals(List.of(), INVENTORY.requests());
	}

	@Test
	void rejectsDeletingAnActiveOffer() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		activateOffer(offer);

		deleteOffer(offer, offerEtag(offer)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").exists());

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(jsonPath("$.data.status").value("ACTIVE"));
		mockMvc.perform(get(ENTRIES + "/" + entry)).andExpect(jsonPath("$.data.status").value("INACTIVE"));
		assertEquals(1, count("composition_slot"));
		assertEquals(1, count("slot_option"));
	}

	@Test
	void rejectsLeavingAnActiveEntryWithoutAnotherActiveOffer() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		setEntryStatus(entry, "ACTIVE");

		deleteOffer(offer, offerEtag(offer)).andExpect(status().isConflict());

		assertEquals(1, offerRepository.count());

		String replacement = newOffer(entry);
		activateOffer(replacement);

		deleteOffer(offer, offerEtag(offer)).andExpect(status().isNoContent());

		assertEquals(1, offerRepository.count());
		mockMvc.perform(get(OFFERS + "/" + replacement)).andExpect(status().isOk());
	}

	@Test
	void rejectsDeletingAnOfferWithAStaleOrWeakEtag() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);

		deleteOffer(offer, "\"rev-999\"").andExpect(status().isPreconditionFailed());
		deleteOffer(offer, "W/" + offerEtag(offer)).andExpect(status().isPreconditionFailed());
		deleteOffer(offer, "\"rev-9223372036854775808\"").andExpect(status().isPreconditionFailed());

		assertFalse(offerRepository.findByEntryId(entry).isEmpty());
	}

	@Test
	void entryStatusChangesDoNotChangeTheStatusOfItsOffers() throws Exception {
		UUID entry = newEntry();
		String active = newOffer(entry);
		activateOffer(active);
		String inactive = newOffer(entry);

		for (String status : List.of("ACTIVE", "INACTIVE", "ARCHIVED", "INACTIVE", "ACTIVE")) {
			setEntryStatus(entry, status);

			assertOfferStatus(active, "ACTIVE");
			assertOfferStatus(inactive, "INACTIVE");
		}
	}

	@Test
	void aConcurrentChangeToTheOfferThatKeepsTheEntryEligibleIsRejected() throws Exception {
		UUID entry = newEntry();
		String deleted = newOffer(entry);
		String kept = newOffer(entry);
		activateOffer(kept);
		setEntryStatus(entry, "ACTIVE");
		String deletedEtag = offerEtag(deleted);

		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		assertThrows(OptimisticLockingFailureException.class, () -> transaction.executeWithoutResult(tx -> {
			CatalogOffer concurrent = offerRepository.findById(UUID.fromString(kept)).orElseThrow();
			assertFalse(concurrent.getSlots().isEmpty());
			// Another request deletes the other offer after the one above was read.
			deleteInAnotherThread(deleted, deletedEtag);
			concurrent.changeStatus(OfferStatus.INACTIVE);
			offerRepository.saveAndFlush(concurrent);
		}));

		assertOfferStatus(kept, "ACTIVE");
		mockMvc.perform(get(OFFERS + "/" + deleted)).andExpect(status().isNotFound());
	}

	@Test
	void aPatchBasedOnTheRevisionBeforeTheDeletionCannotRemoveTheLastActiveOptionOfTheBackupOffer()
			throws Exception {
		UUID entry = newEntry();
		String deleted = newOffer(entry);
		String kept = newOffer(entry);
		activateOffer(kept);
		setEntryStatus(entry, "ACTIVE");
		String keptEtag = offerEtag(kept);
		long deletedVersion = version(deleted);

		// The PATCH reads the backup offer while the deletion holds it, and fails once the deletion commits.
		duringDeletion(() -> offerService.delete(UUID.fromString(deleted), deletedVersion),
				() -> patchOffer(kept, keptEtag, lastOptionInactive(kept)))
				.andExpect(status().isPreconditionFailed());

		mockMvc.perform(get(OFFERS + "/" + deleted)).andExpect(status().isNotFound());
		assertOfferStatus(kept, "ACTIVE");
		String current = offerEtag(kept);
		assertNotEquals(keptEtag, current);
		// The new revision is usable for a legitimate change, which never changes the status of the entry.
		patchOffer(kept, current, lastOptionInactive(kept)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		mockMvc.perform(get(ENTRIES + "/" + entry)).andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	void aDeletionAfterTheBackupOfferLostItsLastActiveOptionIsRejected() throws Exception {
		UUID entry = newEntry();
		String deleted = newOffer(entry);
		String kept = newOffer(entry);
		activateOffer(kept);
		setEntryStatus(entry, "ACTIVE");
		String deletedEtag = offerEtag(deleted);

		patchOffer(kept, offerEtag(kept), lastOptionInactive(kept)).andExpect(status().isOk());

		deleteOffer(deleted, deletedEtag).andExpect(status().isConflict());
		assertEquals(2, offerRepository.count());
		assertOfferStatus(kept, "INACTIVE");
	}

	@Test
	void creatingAnOfferOfAnEntryBeingDeletedFailsWithoutLeavingTheOffer() throws Exception {
		UUID entry = newEntry();
		newOffer(entry);
		setEntryStatus(entry, "ARCHIVED");
		long entryVersion = entryRepository.findById(entry).orElseThrow().getVersion();

		duringDeletion(() -> entryService.delete(entry, entryVersion),
				() -> mockMvc.perform(post(ENTRIES + "/" + entry + "/offers")
						.contentType(MediaType.APPLICATION_JSON).content(offerBody())))
				.andExpect(status().isNotFound());

		assertFalse(entryRepository.existsById(entry));
		assertEquals(0, offerRepository.count());
		assertEquals(0, count("composition_slot"));
		assertEquals(0, count("slot_option"));
	}

	@Test
	void patchingAnOfferOfAnEntryBeingDeletedFails() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		setEntryStatus(entry, "ARCHIVED");
		String offerEtag = offerEtag(offer);
		long entryVersion = entryRepository.findById(entry).orElseThrow().getVersion();

		duringDeletion(() -> entryService.delete(entry, entryVersion),
				() -> patchOffer(offer, offerEtag, lastOptionInactive(offer)))
				.andExpect(status().isPreconditionFailed());

		assertFalse(entryRepository.existsById(entry));
		assertEquals(0, offerRepository.count());
		assertEquals(0, count("slot_option"));
	}

	private UUID newEntry() {
		return entryRepository.save(new CatalogEntry(1L, "Hawaiian burger", "Tasty", imageId, List.of())).getId();
	}

	private String newOffer(UUID entry) throws Exception {
		String response = mockMvc.perform(post(ENTRIES + "/" + entry + "/offers")
				.contentType(MediaType.APPLICATION_JSON).content(offerBody()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

	private void setEntryStatus(UUID entry, String status) throws Exception {
		mockMvc.perform(patch(ENTRIES + "/" + entry).header(HttpHeaders.IF_MATCH, entryEtag(entry))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}"))
				.andExpect(status().isOk());
	}

	private void activateOffer(String offer) throws Exception {
		mockMvc.perform(patch(OFFERS + "/" + offer).header(HttpHeaders.IF_MATCH, offerEtag(offer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
				.andExpect(status().isOk());
	}

	private String entryEtag(UUID entry) throws Exception {
		return mockMvc.perform(get(ENTRIES + "/" + entry)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private String offerEtag(String offer) throws Exception {
		return mockMvc.perform(get(OFFERS + "/" + offer)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private ResultActions deleteEntry(UUID entry, String ifMatch) throws Exception {
		return mockMvc.perform(delete(ENTRIES + "/" + entry).header(HttpHeaders.IF_MATCH, ifMatch));
	}

	private ResultActions deleteOffer(String offer, String ifMatch) throws Exception {
		return mockMvc.perform(delete(OFFERS + "/" + offer).header(HttpHeaders.IF_MATCH, ifMatch));
	}

	private ResultActions patchOffer(String offer, String ifMatch, String json) throws Exception {
		return mockMvc.perform(patch(OFFERS + "/" + offer).header(HttpHeaders.IF_MATCH, ifMatch)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private long version(String offer) {
		return offerRepository.findById(UUID.fromString(offer)).orElseThrow().getVersion();
	}

	/** Inactivates the only option of the offer, so it loses its last ACTIVE slot. */
	private String lastOptionInactive(String offer) throws Exception {
		String json = mockMvc.perform(get(OFFERS + "/" + offer)).andReturn().getResponse().getContentAsString();
		return """
				{"composition": {"slots": [{"slotId": "%s", "options": [
				  {"optionId": "%s", "sourceType": "INVENTORY_ITEM", "status": "INACTIVE"}]}]}}
				""".formatted(JsonPath.<String>read(json, "$.data.composition.slots[0].slotId"),
				JsonPath.<String>read(json, "$.data.composition.slots[0].options[0].optionId"));
	}

	/**
	 * Runs the deletion in a transaction and, while it holds its row locks, sends the request from another
	 * thread. The deletion commits once the request waits for those locks, so the request always runs
	 * between reading the old state and the commit of the deletion.
	 */
	private ResultActions duringDeletion(Runnable deletion, Callable<ResultActions> request) throws Exception {
		CompletableFuture<ResultActions> concurrent = new CompletableFuture<>();
		new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
			deletion.run();
			CompletableFuture.runAsync(() -> {
				try {
					concurrent.complete(request.call());
				}
				catch (Exception e) {
					concurrent.completeExceptionally(e);
				}
			});
			awaitBlockedRequest(concurrent);
		});
		return concurrent.get(10, TimeUnit.SECONDS);
	}

	private void awaitBlockedRequest(CompletableFuture<ResultActions> request) {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (blockedSessions() == 0) {
			if (request.isDone() || System.nanoTime() > deadline) {
				throw new AssertionError("The concurrent request did not wait for the locks of the deletion");
			}
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
		}
	}

	private long blockedSessions() {
		return jdbcTemplate.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
				+ " and datname = current_database()", Long.class);
	}

	private void deleteInAnotherThread(String offer, String ifMatch) {
		CompletableFuture.runAsync(() -> {
			try {
				deleteOffer(offer, ifMatch).andExpect(status().isNoContent());
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}).join();
	}

	private void assertOfferStatus(String offer, String expected) throws Exception {
		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(jsonPath("$.data.status").value(expected));
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
	}

	private String offerBody() {
		return """
				{"presentationTag": "Individual", "basePrice": 180, "imageId": "%s", "composition": {"slots": [
				  {"name": "Side", "quantity": 1, "options": [
				    {"displayName": "Fries", "sourceType": "INVENTORY_ITEM", "inventoryItemId": "%s",
				     "quantity": 200, "unit": "g"}]}]}}
				""".formatted(imageId, POTATOES.id());
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
