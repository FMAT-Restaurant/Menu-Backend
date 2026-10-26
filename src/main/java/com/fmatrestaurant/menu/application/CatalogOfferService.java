package com.fmatrestaurant.menu.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.CompositionSlot;
import com.fmatrestaurant.menu.domain.EntryStatus;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.domain.SlotOption;
import com.fmatrestaurant.menu.domain.SourceType;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;
import com.fmatrestaurant.menu.infrastructure.InventoryClient;
import com.fmatrestaurant.menu.infrastructure.RecipeRepository;
import com.fmatrestaurant.menu.infrastructure.RecipeRevisionRepository;

/**
 * Offer and composition use cases (REQ-MENU-OFFER-001 to REQ-MENU-OFFER-004, REQ-MENU-COMP-001 to
 * REQ-MENU-COMP-005, REQ-MENU-CONT-001 and REQ-MENU-CONT-002 with Inventory content).
 *
 * <p>Each write runs in a transaction and validates the whole result, so a rejected request leaves
 * no partial changes. Inventory articles are confirmed with the Inventory service before saving.
 */
@Service
public class CatalogOfferService {

	private final CatalogOfferRepository offerRepository;

	private final CatalogEntryRepository entryRepository;

	private final ImageRepository imageRepository;

	private final InventoryClient inventoryClient;

	private final RecipeRevisionRepository recipeRevisionRepository;

	private final RecipeRepository recipeRepository;

	public CatalogOfferService(CatalogOfferRepository offerRepository, CatalogEntryRepository entryRepository,
			ImageRepository imageRepository, InventoryClient inventoryClient,
			RecipeRevisionRepository recipeRevisionRepository, RecipeRepository recipeRepository) {
		this.offerRepository = offerRepository;
		this.entryRepository = entryRepository;
		this.imageRepository = imageRepository;
		this.inventoryClient = inventoryClient;
		this.recipeRevisionRepository = recipeRevisionRepository;
		this.recipeRepository = recipeRepository;
	}

	/**
	 * Creates an offer of the entry. It always starts {@link OfferStatus#INACTIVE} and its options start
	 * {@link OfferStatus#ACTIVE}; the server assigns every status and id, so the client cannot send them.
	 *
	 * @throws CatalogEntryNotFoundException if the entry does not exist
	 * @throws InvalidFieldException if a field is not defined by the contract, a value is missing or invalid,
	 *         the image or an inventory item does not exist, or the composition is empty
	 * @throws org.springframework.web.client.RestClientException if Inventory cannot confirm the articles
	 */
	@Transactional
	public CatalogOffer create(UUID entryId, OfferInput input) {
		CatalogEntry entry = entryRepository.findById(entryId)
				.orElseThrow(() -> new CatalogEntryNotFoundException(entryId));
		input.rejectUnknownFields();
		notAllowed("/status", input.status());
		if (input.composition() != null) {
			input.composition().rejectUnknownFields("/composition");
		}
		UUID imageId = existingImage(input.imageId());
		List<SlotInput> slots = input.composition() == null ? null : input.composition().slots();
		CatalogOffer offer = new CatalogOffer(entry, input.presentationTag(), input.basePrice(), imageId,
				slots(List.of(), slots, true, new HashMap<>()));
		try {
			offerRepository.saveAndFlush(offer);
			offerRepository.recordRevision(offer);
			return offer;
		}
		catch (DataIntegrityViolationException _) {
			// ponytail: the only constraint an offer can violate here is the foreign key to its entry, deleted
			// after it was read; if another constraint is added, check which one failed instead of answering 404.
			throw new CatalogEntryNotFoundException(entryId);
		}
	}

	/**
	 * @throws CatalogOfferNotFoundException if the offer does not exist
	 */
	@Transactional(readOnly = true)
	public CatalogOffer get(UUID id) {
		return offerRepository.findById(id).orElseThrow(() -> new CatalogOfferNotFoundException(id));
	}

	/**
	 * @param entryId only offers of this entry
	 * @param excludeOfferId leaves this offer out
	 * @param page page number, starting at 1
	 * @throws InvalidFieldException if the page or the page size are not valid
	 */
	@Transactional(readOnly = true)
	public Page<CatalogOffer> list(UUID entryId, UUID excludeOfferId, int page, int pageSize) {
		Pages.check(page, pageSize);
		return offerRepository.findAll(CatalogOfferRepository.search(entryId, excludeOfferId),
				PageRequest.of(page - 1, pageSize, Sort.by("entry.brandName", "presentationTag", "id")));
	}

	/**
	 * Recursive update: missing values keep the current ones. Sent {@code slots} or {@code options}
	 * are the complete desired set: existing members, identified by id, keep their omitted fields;
	 * members not sent are removed; members without id are created. The status of the offer is then
	 * recalculated from its slots and its administrative intent.
	 *
	 * @param expectedVersion the version the client read
	 * @throws CatalogOfferNotFoundException if the offer does not exist
	 * @throws StaleCatalogOfferException if the offer changed since the expected version
	 * @throws InvalidFieldException if a field is not defined by the contract, a value or id is invalid,
	 *         unknown, foreign or repeated, a list is empty, the image or an inventory item does not exist, or
	 *         the offer cannot be activated
	 * @throws org.springframework.web.client.RestClientException if Inventory cannot confirm the articles
	 */
	@Transactional
	public CatalogOffer update(UUID id, long expectedVersion, OfferInput input) {
		CatalogOffer offer = offerRepository.findWithLockById(id)
				.orElseThrow(() -> new CatalogOfferNotFoundException(id));
		if (offer.getVersion() != expectedVersion) {
			throw new StaleCatalogOfferException(id);
		}
		input.rejectUnknownFields();
		if (input.composition() != null) {
			input.composition().rejectUnknownFields("/composition");
		}
		offer.update(input.presentationTag(), input.basePrice(), existingImage(input.imageId()));
		if (input.composition() != null && input.composition().slots() != null) {
			offer.replaceSlots(slots(offer.getSlots(), input.composition().slots(), false, new HashMap<>()));
		}
		if (input.status() != null) {
			offer.changeStatus(input.status());
		}
		offerRepository.saveAndFlush(offer);
		offerRepository.recordRevision(offer);
		return offer;
	}

	/**
	 * Deletes an inactive offer, with its slots and options. If its entry is ACTIVE, the entry must keep
	 * at least one other ACTIVE offer; this is checked even though the offer is already inactive.
	 *
	 * <p>The entry row stays locked while the preconditions are checked, so two deletions of offers of
	 * the same entry cannot both rely on an eligibility that the other one removes.
	 *
	 * @param expectedVersion the version the client read
	 * @throws CatalogOfferNotFoundException if the offer does not exist
	 * @throws StaleCatalogOfferException if the offer changed since the expected version
	 * @throws DeletionNotAllowedException if the offer is ACTIVE, or the entry would be left ACTIVE
	 *         without an ACTIVE offer
	 */
	@Transactional
	public void delete(UUID id, long expectedVersion) {
		UUID entryId = offerRepository.findEntryIdById(id).orElseThrow(() -> new CatalogOfferNotFoundException(id));
		CatalogEntry entry = entryRepository.findWithLockById(entryId)
				.orElseThrow(() -> new CatalogOfferNotFoundException(id));
		CatalogOffer offer = offerRepository.findForDeletionById(id)
				.orElseThrow(() -> new CatalogOfferNotFoundException(id));
		long currentVersion = offer.getVersion() == null ? 0 : offer.getVersion();
		if (currentVersion != expectedVersion) {
			throw new StaleCatalogOfferException(id);
		}
		if (offer.getStatus() != OfferStatus.INACTIVE) {
			throw new DeletionNotAllowedException("Only an INACTIVE offer can be deleted");
		}
		if (entry.getStatus() == EntryStatus.ACTIVE) {
			requireAnotherActiveOffer(entryId, id);
		}
		offerRepository.delete(offer);
		offerRepository.flush();
	}

	/**
	 * Checks that another offer of the entry is ACTIVE. The version of that offer is incremented, and its row
	 * stays locked, so a concurrent change based on its previous version, such as inactivating it, is
	 * rejected instead of leaving the entry without an ACTIVE offer once this one is deleted.
	 */
	private void requireAnotherActiveOffer(UUID entryId, UUID deletedOfferId) {
		CatalogOffer active = offerRepository.findByEntryId(entryId).stream()
				.filter(other -> !other.getId().equals(deletedOfferId) && other.getStatus() == OfferStatus.ACTIVE)
				.findFirst()
				.orElseThrow(() -> new DeletionNotAllowedException(
						"An ACTIVE entry must keep at least one other ACTIVE offer; inactivate or archive the entry first"));
		if (offerRepository.incrementVersion(active.getId(), active.getVersion()) != 1) {
			throw new DeletionNotAllowedException("The offers of the entry changed while the deletion was checked; try again");
		}
	}

	/**
	 * @param create when {@code true}, every slot and option is new and ids or statuses are not allowed
	 * @param items the Inventory articles already confirmed in this operation, by id
	 */
	private List<CompositionSlot> slots(List<CompositionSlot> current, List<SlotInput> inputs, boolean create,
			Map<String, InventoryItem> items) {
		if (inputs == null || inputs.isEmpty()) {
			throw new InvalidFieldException("/composition/slots", "The composition must have at least one slot");
		}
		Map<UUID, CompositionSlot> byId = current.stream()
				.collect(Collectors.toMap(CompositionSlot::getId, Function.identity()));
		Set<UUID> seen = new HashSet<>();
		List<CompositionSlot> result = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			try {
				result.add(slot(byId, seen, inputs.get(i), create, items));
			} catch (InvalidFieldException e) {
				throw e.prefixed("/composition/slots/" + i);
			}
		}
		return result;
	}

	private CompositionSlot slot(Map<UUID, CompositionSlot> byId, Set<UUID> seen, SlotInput input, boolean create,
			Map<String, InventoryItem> items) {
		if (input == null) {
			throw new InvalidFieldException("", "The slot is required");
		}
		input.rejectUnknownFields();
		if (create) {
			notAllowed("/slotId", input.slotId());
		}
		UUID id = input.slotId();
		if (id == null) {
			return new CompositionSlot(input.name(), input.quantity(), input.course(),
					options(List.of(), input.options(), create, items));
		}
		CompositionSlot slot = existing(byId, seen, id, "/slotId", "slot", "offer");
		slot.update(input.name(), input.quantity());
		if (input.courseSent()) {
			slot.changeCourse(input.course());
		}
		if (input.options() != null) {
			slot.replaceOptions(options(slot.getOptions(), input.options(), false, items));
		}
		return slot;
	}

	private List<SlotOption> options(List<SlotOption> current, List<OptionInput> inputs, boolean create,
			Map<String, InventoryItem> items) {
		if (inputs == null || inputs.isEmpty()) {
			throw new InvalidFieldException("/options", "A slot must have at least one option");
		}
		Map<UUID, SlotOption> byId = current.stream().collect(Collectors.toMap(SlotOption::getId, Function.identity()));
		Set<UUID> seen = new HashSet<>();
		List<SlotOption> result = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			try {
				result.add(option(byId, seen, inputs.get(i), create, items));
			} catch (InvalidFieldException e) {
				throw e.prefixed("/options/" + i);
			}
		}
		return result;
	}

	/**
	 * Inventory is consulted for a new option and when the article or the unit of an existing one change;
	 * other changes keep the stored reference without consulting it.
	 */
	private SlotOption option(Map<UUID, SlotOption> byId, Set<UUID> seen, OptionInput input, boolean create,
			Map<String, InventoryItem> items) {
		if (input == null) {
			throw new InvalidFieldException("", "The option is required");
		}
		input.rejectUnknownFields();
		if (create) {
			notAllowed("/optionId", input.optionId());
			notAllowed("/status", input.status());
		}
		if (input.sourceType() == null) {
			throw new InvalidFieldException("/sourceType", "The source type is required");
		}
		UUID id = input.optionId();
		if (id == null) {
			if (input.sourceType() == SourceType.RECIPE) {
				rejectInventoryFields(input);
				return new SlotOption(input.displayName(), create ? OfferStatus.ACTIVE : input.status(),
						input.recipeId(), latestRecipeRevision(input.recipeId()));
			}
			rejectRecipeFields(input);
			return new SlotOption(input.displayName(), create ? OfferStatus.ACTIVE : input.status(),
					inventoryItem(input.inventoryItemId(), items), input.quantity(), input.unit());
		}
		SlotOption option = existing(byId, seen, id, "/optionId", "option", "slot");
		if (input.sourceType() != option.getSourceType()) {
			throw new InvalidFieldException("/sourceType", "The source type of an option cannot change");
		}
		if (option.getSourceType() == SourceType.RECIPE) {
			updateRecipeOption(option, input);
			return option;
		}
		updateInventoryOption(option, input, items);
		return option;
	}

	private void updateInventoryOption(SlotOption option, OptionInput input, Map<String, InventoryItem> items) {
		rejectRecipeFields(input);
		InventoryItem item = null;
		if (input.inventoryItemId() != null || input.unit() != null) {
			item = inventoryItem(input.inventoryItemId() != null ? input.inventoryItemId() : option.getInventoryItemId(),
					items);
		}
		option.update(input.displayName(), input.status(), item, input.quantity(), input.unit());
	}

	/**
	 * A recipe option has no Inventory content. Sending its recipe, even the same one, fixes the latest
	 * revision again; omitting it keeps the recipe and the revision.
	 */
	private void updateRecipeOption(SlotOption option, OptionInput input) {
		rejectInventoryFields(input);
		Integer revision = input.recipeId() == null ? null : latestRecipeRevision(input.recipeId());
		option.updateRecipe(input.displayName(), input.status(), input.recipeId(), revision);
	}

	private static void rejectInventoryFields(OptionInput input) {
		notAllowed("/inventoryItemId", input.inventoryItemId());
		notAllowed("/quantity", input.quantity());
		notAllowed("/unit", input.unit());
	}

	private static void rejectRecipeFields(OptionInput input) {
		notAllowed("/recipeId", input.recipeId());
	}

	/**
	 * The current published revision of a recipe of the library, which the option is fixed to.
	 *
	 * <p>A shared lock keeps the recipe in the library until commit and serializes the assignment with the
	 * publication of another revision, which updates the recipe row in its own transaction: whichever
	 * commits first decides the revision fixed here.
	 */
	private int latestRecipeRevision(UUID recipeId) {
		if (recipeId == null) {
			throw new InvalidFieldException("/recipeId", "The recipe id is required");
		}
		int revision = recipeRepository.findForReferenceById(recipeId)
				.orElseThrow(() -> new InvalidFieldException("/recipeId", "The recipe is not in the library"))
				.getCurrentRevision();
		if (!recipeRevisionRepository.existsByRecipeIdAndRevision(recipeId, revision)) {
			throw new InvalidFieldException("/recipeId", "The recipe " + recipeId + " has no valid revision");
		}
		return revision;
	}

	private static <T> T existing(Map<UUID, T> byId, Set<UUID> seen, UUID id, String path, String label,
			String owner) {
		T member = byId.get(id);
		if (member == null) {
			throw new InvalidFieldException(path, "The " + label + " " + id + " does not belong to this " + owner);
		}
		if (!seen.add(id)) {
			throw new InvalidFieldException(path, "The " + label + " " + id + " is repeated");
		}
		return member;
	}

	/** A field that the server assigns, so the client must not send it. */
	private static void notAllowed(String path, Object value) {
		if (value != null) {
			throw new InvalidFieldException(path, "The field " + path.substring(1) + " is not allowed");
		}
	}

	/**
	 * The article as Inventory returns it, consulted once per id in each operation. Its unit is the one
	 * the option unit is checked against.
	 */
	private InventoryItem inventoryItem(String id, Map<String, InventoryItem> items) {
		if (id == null || id.isBlank()) {
			throw new InvalidFieldException("/inventoryItemId", "The inventory item id is required");
		}
		return items.computeIfAbsent(id, key -> inventoryClient.find(key).orElseThrow(
				() -> new InvalidFieldException("/inventoryItemId", "The inventory item " + key + " does not exist")));
	}

	/** {@code null} when the image was not sent. */
	private UUID existingImage(UUID imageId) {
		if (imageId == null) {
			return null;
		}
		if (!imageRepository.existsById(imageId)) {
			throw new InvalidFieldException("/imageId", "The image " + imageId + " does not exist");
		}
		return imageId;
	}

	/**
	 * Fields of a request body that the contract does not define are rejected
	 * ({@code additionalProperties: false}), which also rejects mixing source types.
	 *
	 * <p>A field sent as {@code null} fails when it is read ({@link Nulls#FAIL}), unlike a missing one. The
	 * only field that accepts {@code null} is the course of a slot, so a setter records that it was sent.
	 */
	abstract static class Input {

		private final List<String> unknownFields = new ArrayList<>();

		@JsonAnySetter
		private void unknownField(String name, Object ignoredValue) {
			unknownFields.add(name);
		}

		void rejectUnknownFields() {
			rejectUnknownFields("");
		}

		void rejectUnknownFields(String path) {
			if (!unknownFields.isEmpty()) {
				throw new InvalidFieldException(path + "/" + unknownFields.getFirst(),
						"The field " + unknownFields.getFirst() + " is not allowed");
			}
		}

	}

	/** Body of POST and PATCH; on PATCH, missing values keep the current ones. */
	public static class OfferInput extends Input {

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private String presentationTag;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private BigDecimal basePrice;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private OfferStatus status;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private UUID imageId;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private CompositionInput composition;

		public String presentationTag() {
			return presentationTag;
		}

		public BigDecimal basePrice() {
			return basePrice;
		}

		public OfferStatus status() {
			return status;
		}

		public UUID imageId() {
			return imageId;
		}

		public CompositionInput composition() {
			return composition;
		}

	}

	public static class CompositionInput extends Input {

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private List<SlotInput> slots;

		public List<SlotInput> slots() {
			return slots;
		}

	}

	public static class SlotInput extends Input {

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private UUID slotId;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private String name;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private BigDecimal quantity;

		/** Optional on creation; sent as {@code null}, it removes the course on PATCH. */
		private String course;

		private boolean courseSent;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private List<OptionInput> options;

		public UUID slotId() {
			return slotId;
		}

		public String name() {
			return name;
		}

		public BigDecimal quantity() {
			return quantity;
		}

		public String course() {
			return course;
		}

		public boolean courseSent() {
			return courseSent;
		}

		@JsonProperty("course")
		private void setCourse(String course) {
			this.course = course;
			this.courseSent = true;
		}

		public List<OptionInput> options() {
			return options;
		}

	}

	public static class OptionInput extends Input {

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private UUID optionId;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private String displayName;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private OfferStatus status;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private SourceType sourceType;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private String inventoryItemId;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private BigDecimal quantity;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private String unit;

		@JsonProperty
		@JsonSetter(nulls = Nulls.FAIL)
		private UUID recipeId;

		public UUID recipeId() {
			return recipeId;
		}

		public UUID optionId() {
			return optionId;
		}

		public String displayName() {
			return displayName;
		}

		public OfferStatus status() {
			return status;
		}

		public SourceType sourceType() {
			return sourceType;
		}

		public String inventoryItemId() {
			return inventoryItemId;
		}

		public BigDecimal quantity() {
			return quantity;
		}

		public String unit() {
			return unit;
		}

	}

}
