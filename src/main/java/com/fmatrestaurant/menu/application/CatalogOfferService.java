package com.fmatrestaurant.menu.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fmatrestaurant.menu.domain.CatalogEntry;
import com.fmatrestaurant.menu.domain.CatalogOffer;
import com.fmatrestaurant.menu.domain.CompositionSlot;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.domain.InventoryItem;
import com.fmatrestaurant.menu.domain.OfferStatus;
import com.fmatrestaurant.menu.domain.SlotOption;
import com.fmatrestaurant.menu.domain.SourceType;
import com.fmatrestaurant.menu.infrastructure.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;

/**
 * Offer and composition use cases (REQ-MENU-OFFER-001 to REQ-MENU-OFFER-004, REQ-MENU-COMP-001 to
 * REQ-MENU-COMP-005, REQ-MENU-CONT-001 and REQ-MENU-CONT-002 with Inventory content).
 *
 * <p>Each write runs in a transaction and validates the whole result, so a rejected request leaves
 * no partial changes.
 */
@Service
public class CatalogOfferService {

	private final CatalogOfferRepository offerRepository;

	private final CatalogEntryRepository entryRepository;

	private final ImageRepository imageRepository;

	private final InventoryItemService inventoryItemService;

	public CatalogOfferService(CatalogOfferRepository offerRepository, CatalogEntryRepository entryRepository,
			ImageRepository imageRepository, InventoryItemService inventoryItemService) {
		this.offerRepository = offerRepository;
		this.entryRepository = entryRepository;
		this.imageRepository = imageRepository;
		this.inventoryItemService = inventoryItemService;
	}

	/**
	 * Creates an offer of the entry. It always starts {@link OfferStatus#INACTIVE}: the status and the
	 * slot and option ids sent by the client are ignored, the server assigns them.
	 *
	 * @throws CatalogEntryNotFoundException if the entry does not exist
	 * @throws InvalidFieldException if a value is missing or invalid, the image or an inventory item
	 *         does not exist, or the composition is empty
	 * @throws InventoryUnavailableException if Inventory cannot answer
	 */
	@Transactional
	public CatalogOffer create(UUID entryId, OfferInput input) {
		CatalogEntry entry = entryRepository.findById(entryId)
				.orElseThrow(() -> new CatalogEntryNotFoundException(entryId));
		UUID imageId = existingImage(input.imageId());
		List<SlotInput> slots = input.composition() == null ? null : input.composition().slots();
		CatalogOffer offer = new CatalogOffer(entry, input.presentationTag(), input.basePrice(), imageId,
				slots(List.of(), slots, true));
		return offerRepository.save(offer);
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
	 *         unknown, foreign or repeated, a list is empty, the image or an inventory item does not exist, or the offer cannot be activated
	 * @throws InventoryUnavailableException if Inventory cannot answer
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
			offer.replaceSlots(slots(offer.getSlots(), input.composition().slots(), false));
		}
		if (input.status() != null) {
			offer.changeStatus(input.status());
		}
		return offerRepository.saveAndFlush(offer);
	}

	/**
	 * @param create when {@code true}, every slot and option is new and their ids are ignored
	 */
	private List<CompositionSlot> slots(List<CompositionSlot> current, List<SlotInput> inputs, boolean create) {
		if (inputs == null || inputs.isEmpty()) {
			throw new InvalidFieldException("/composition/slots", "The composition must have at least one slot");
		}
		Map<UUID, CompositionSlot> byId = current.stream()
				.collect(Collectors.toMap(CompositionSlot::getId, Function.identity()));
		Set<UUID> seen = new HashSet<>();
		List<CompositionSlot> result = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			try {
				result.add(slot(byId, seen, inputs.get(i), create));
			} catch (InvalidFieldException e) {
				throw e.prefixed("/composition/slots/" + i);
			}
		}
		return result;
	}

	private CompositionSlot slot(Map<UUID, CompositionSlot> byId, Set<UUID> seen, SlotInput input, boolean create) {
		if (input == null) {
			throw new InvalidFieldException("", "The slot is required");
		}
		if (!create) {
			input.rejectUnknownFields();
		}
		if (Boolean.FALSE.equals(input.required())) {
			throw new InvalidFieldException("/required", "Every slot takes part in the selection; required must be true");
		}
		UUID id = create ? null : input.slotId();
		if (id == null) {
			String course = input.course() == null ? null : input.course().orElse(null);
			return new CompositionSlot(input.name(), input.quantity(), course,
					options(List.of(), input.options(), create));
		}
		CompositionSlot slot = existing(byId, seen, id, "/slotId", "slot", "offer");
		slot.update(input.name(), input.quantity(), input.course());
		if (input.options() != null) {
			slot.replaceOptions(options(slot.getOptions(), input.options(), false));
		}
		return slot;
	}

	private List<SlotOption> options(List<SlotOption> current, List<OptionInput> inputs, boolean create) {
		if (inputs == null || inputs.isEmpty()) {
			throw new InvalidFieldException("/options", "A slot must have at least one option");
		}
		Map<UUID, SlotOption> byId = current.stream().collect(Collectors.toMap(SlotOption::getId, Function.identity()));
		Set<UUID> seen = new HashSet<>();
		List<SlotOption> result = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			try {
				result.add(option(byId, seen, inputs.get(i), create));
			} catch (InvalidFieldException e) {
				throw e.prefixed("/options/" + i);
			}
		}
		return result;
	}

	private SlotOption option(Map<UUID, SlotOption> byId, Set<UUID> seen, OptionInput input, boolean create) {
		if (input == null) {
			throw new InvalidFieldException("", "The option is required");
		}
		if (!create) {
			input.rejectUnknownFields();
		}
		UUID id = create ? null : input.optionId();
		if (id == null) {
			if (input.sourceType() == null) {
				throw new InvalidFieldException("/sourceType", "The source type is required");
			}
			if (input.inventoryItemId() == null) {
				throw new InvalidFieldException("/inventoryItemId", "The inventory item is required");
			}
			return new SlotOption(input.displayName(), input.status(), inventoryItem(input.inventoryItemId()),
					input.quantity(), input.unit());
		}
		SlotOption option = existing(byId, seen, id, "/optionId", "option", "slot");
		// Inventory is only asked when the article or the unit change.
		boolean newItem = input.inventoryItemId() != null && !input.inventoryItemId().equals(option.getInventoryItemId());
		boolean newUnit = input.unit() != null && !input.unit().strip().equals(option.getUnit());
		InventoryItem item = newItem || newUnit
				? inventoryItem(newItem ? input.inventoryItemId() : option.getInventoryItemId()) : null;
		option.update(input.displayName(), input.status(), item, input.quantity(), item == null ? null : input.unit());
		return option;
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

	private InventoryItem inventoryItem(UUID id) {
		return inventoryItemService.find(id).orElseThrow(
				() -> new InvalidFieldException("/inventoryItemId", "The inventory item " + id + " does not exist"));
	}

	private UUID existingImage(Optional<UUID> image) {
		if (image == null) {
			return null;
		}
		UUID imageId = image.orElseThrow(() -> new InvalidFieldException("/imageId", "The image must not be null"));
		if (!imageRepository.existsById(imageId)) {
			throw new InvalidFieldException("/imageId", "The image " + imageId + " does not exist");
		}
		return imageId;
	}

	/**
	 * Fields of a request body that the contract does not define. POST ignores them, because the
	 * contract examples send ids and fields of other sources; PATCH rejects them
	 * ({@code additionalProperties: false}), which also rejects mixing source types.
	 *
	 * <p>The bodies are classes with fields, not records: Jackson gives a record
	 * {@code Optional.empty()} for a missing value too, so only fields tell a missing value from
	 * {@code null}.
	 */
	abstract static class Input {

		private final List<String> unknownFields = new ArrayList<>();

		@JsonAnySetter
		private void unknownField(String name, Object value) {
			unknownFields.add(name);
		}

		void rejectUnknownFields() {
			rejectUnknownFields("");
		}

		void rejectUnknownFields(String path) {
			if (!unknownFields.isEmpty()) {
				throw new InvalidFieldException(path + "/" + unknownFields.get(0),
						"The field " + unknownFields.get(0) + " is not allowed");
			}
		}

	}

	/** Body of POST and PATCH; on PATCH, missing values keep the current ones, and so does {@code null}. */
	public static class OfferInput extends Input {

		@JsonProperty
		private String presentationTag;

		@JsonProperty
		private BigDecimal basePrice;

		@JsonProperty
		private OfferStatus status;

		/** {@code null} when missing, empty when sent as {@code null}, which is rejected. */
		@JsonProperty
		private Optional<UUID> imageId;

		@JsonProperty
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

		public Optional<UUID> imageId() {
			return imageId;
		}

		public CompositionInput composition() {
			return composition;
		}

	}

	public static class CompositionInput extends Input {

		@JsonProperty
		private List<SlotInput> slots;

		public List<SlotInput> slots() {
			return slots;
		}

	}

	public static class SlotInput extends Input {

		@JsonProperty
		private UUID slotId;

		@JsonProperty
		private String name;

		@JsonProperty
		private BigDecimal quantity;

		/** {@code null} when missing, empty when sent as {@code null} to remove the course. */
		@JsonProperty
		private Optional<String> course;

		@JsonProperty
		private Boolean required;

		@JsonProperty
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

		public Optional<String> course() {
			return course;
		}

		public Boolean required() {
			return required;
		}

		public List<OptionInput> options() {
			return options;
		}

	}

	public static class OptionInput extends Input {

		@JsonProperty
		private UUID optionId;

		@JsonProperty
		private String displayName;

		@JsonProperty
		private OfferStatus status;

		@JsonProperty
		private SourceType sourceType;

		@JsonProperty
		private UUID inventoryItemId;

		@JsonProperty
		private BigDecimal quantity;

		@JsonProperty
		private String unit;

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

		public UUID inventoryItemId() {
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
