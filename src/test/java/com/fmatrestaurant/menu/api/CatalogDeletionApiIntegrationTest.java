package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * HTTP flows to delete entries and offers against PostgreSQL (Testcontainers). Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class CatalogDeletionApiIntegrationTest {

	private static final String ENTRIES = "/api/v1/menu/entries";

	private static final String OFFERS = "/api/v1/menu/offers";

	private static final UUID POTATOES = UUID.randomUUID();

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

	private UUID imageId;

	@BeforeEach
	void setUp() throws IOException {
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

		deleteEntry(entry, entryEtag(entry)).andExpect(status().isNoContent());

		mockMvc.perform(get(ENTRIES + "/" + entry)).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/" + firstOffer)).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/" + secondOffer)).andExpect(status().isNotFound());
		assertEquals(0, offerRepository.count());
		assertEquals(0, count("composition_slot"));
		assertEquals(0, count("slot_option"));
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
	void rejectsDeletingAnEntryWithAStaleEtag() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		setEntryStatus(entry, "ARCHIVED");

		deleteEntry(entry, "W/\"rev-999\"").andExpect(status().isPreconditionFailed());
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
		deleteEntry(UUID.randomUUID(), "W/\"rev-0\"").andExpect(status().isNotFound());
		deleteOffer(UUID.randomUUID().toString(), "\"rev-0\"").andExpect(status().isNotFound());
	}

	@Test
	void deletesAnInactiveOfferAndKeepsItsEntryAndTheOtherOffers() throws Exception {
		UUID entry = newEntry();
		String offer = newOffer(entry);
		String other = newOffer(entry);
		assertEquals(2, count("composition_slot"));
		assertEquals(2, count("slot_option"));

		deleteOffer(offer, offerEtag(offer)).andExpect(status().isNoContent());

		mockMvc.perform(get(OFFERS + "/" + offer)).andExpect(status().isNotFound());
		mockMvc.perform(get(OFFERS + "/" + other)).andExpect(status().isOk());
		assertTrue(entryRepository.existsById(entry));
		assertEquals(1, count("composition_slot"));
		assertEquals(1, count("slot_option"));
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
				    {"displayName": "Fries", "status": "ACTIVE", "sourceType": "INVENTORY_ITEM",
				     "source": {"id": "%s", "name": "Potatoes", "unit": "g"}, "quantity": 200, "unit": "g"}]}]}}
				""".formatted(imageId, POTATOES);
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
