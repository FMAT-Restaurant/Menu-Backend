package com.fmatrestaurant.menu.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Selection space of a composition (BR-MENU-006). It has one or more options and its status is
 * derived from them: ACTIVE if at least one option is ACTIVE (BR-MENU-016).
 *
 * <p>Every ACTIVE slot takes part in the selection with {@code quantity} rounds, choosing exactly
 * one ACTIVE option per round; an INACTIVE slot generates no rounds (BR-MENU-008 to BR-MENU-011).
 */
@Entity
@Table(name = "composition_slot")
public class CompositionSlot {

	/** Maximum length of the name, matching the database column. */
	public static final int NAME_MAX_LENGTH = 255;

	/** Accepted courses (BR-MENU-013). */
	public static final Set<String> COURSES = Set.of("entrada", "plato fuerte", "postre", "bebida");

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(nullable = false)
	private int quantity;

	@Column(length = 16)
	private String course;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "slot_id", nullable = false)
	@OrderColumn(name = "position")
	private List<SlotOption> options = new ArrayList<>();

	protected CompositionSlot() {
		// Required by JPA.
	}

	/**
	 * @param course optional; {@code null} means no course
	 * @throws InvalidFieldException if a value is missing or invalid, or there are no options
	 */
	public CompositionSlot(String name, BigDecimal quantity, String course, List<SlotOption> options) {
		this.name = Fields.text("/name", "slot name", name, NAME_MAX_LENGTH);
		this.quantity = quantity(quantity);
		this.course = course(course);
		replaceOptions(options);
	}

	/**
	 * Applies the given changes; a {@code null} value keeps the current one. If any value is
	 * invalid, nothing is modified.
	 *
	 * @throws InvalidFieldException if a value is invalid
	 */
	public void update(String name, BigDecimal quantity) {
		String newName = name == null ? this.name : Fields.text("/name", "slot name", name, NAME_MAX_LENGTH);
		int newQuantity = quantity == null ? this.quantity : quantity(quantity);
		this.name = newName;
		this.quantity = newQuantity;
	}

	/**
	 * @param course the new course, or {@code null} to remove it
	 * @throws InvalidFieldException if the course is not one of {@link #COURSES}
	 */
	public void changeCourse(String course) {
		this.course = course(course);
	}

	/**
	 * Replaces the options; options that are not given are removed.
	 *
	 * @throws InvalidFieldException if there are no options
	 */
	public void replaceOptions(List<SlotOption> newOptions) {
		if (newOptions == null || newOptions.isEmpty()) {
			throw new InvalidFieldException("/options", "A slot must have at least one option");
		}
		// Replacing an equal list would still mark the collection dirty.
		if (!options.equals(newOptions)) {
			options.clear();
			options.addAll(newOptions);
		}
	}

	/** ponytail: OPEN-006 leaves the range open; rounds are counted, so a positive whole number. */
	private static int quantity(BigDecimal quantity) {
		if (quantity == null) {
			throw new InvalidFieldException("/quantity", "The slot quantity is required");
		}
		try {
			int rounds = quantity.intValueExact();
			if (rounds >= 1) {
				return rounds;
			}
		} catch (ArithmeticException _) {
			// Not a whole number: rejected below.
		}
		throw new InvalidFieldException("/quantity", "The slot quantity must be a positive whole number");
	}

	private static String course(String course) {
		if (course != null && !COURSES.contains(course)) {
			throw new InvalidFieldException("/course", "The course must be one of entrada, plato fuerte, postre or bebida");
		}
		return course;
	}

	/** ACTIVE if at least one option is ACTIVE; it is never managed on its own. */
	public OfferStatus getStatus() {
		return options.stream().anyMatch(option -> option.getStatus() == OfferStatus.ACTIVE)
				? OfferStatus.ACTIVE : OfferStatus.INACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getCourse() {
		return course;
	}

	public List<SlotOption> getOptions() {
		return Collections.unmodifiableList(options);
	}

}
