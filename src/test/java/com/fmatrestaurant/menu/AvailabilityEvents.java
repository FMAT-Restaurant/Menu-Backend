package com.fmatrestaurant.menu;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fmatrestaurant.menu.infrastructure.rabbit.RabbitMqConfig;
import com.fmatrestaurant.menu.infrastructure.repository.ProcessedEventRepository;

/**
 * Plays Inventory: publishes {@code inventory.source.availability.updated} in the RabbitMQ of Testcontainers, as the
 * AsyncAPI contract defines it, and waits until Menu applied it. Use it to prepare recipes and Inventory options
 * whose availability Inventory already communicated, instead of writing it in the database.
 */
public final class AvailabilityEvents {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final RabbitTemplate rabbitTemplate;

	private final ProcessedEventRepository processedEvents;

	private final String routingKey;

	public AvailabilityEvents(RabbitTemplate rabbitTemplate, ProcessedEventRepository processedEvents,
			String routingKey) {
		this.rabbitTemplate = rabbitTemplate;
		this.processedEvents = processedEvents;
		this.routingKey = routingKey;
	}

	/** Publishes a new event for the source, waits until Menu applied it and returns its id. */
	public String publish(UUID sourceId, boolean available) {
		String eventId = UUID.randomUUID().toString();
		send(eventId, payload(sourceId, available));
		awaitProcessed(eventId);
		return eventId;
	}

	/** Publishes again an event already sent, with the same id, and waits until Menu consumed it. */
	public void republish(String eventId, UUID sourceId, boolean available) {
		send(eventId, payload(sourceId, available));
		awaitDrained();
	}

	/** Publishes a message with the given body, as a broken or foreign publisher would. */
	public void send(String eventId, String body) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		properties.setMessageId(eventId);
		rabbitTemplate.send(RabbitMqConfig.INVENTORY_EXCHANGE, routingKey,
				new Message(body.getBytes(StandardCharsets.UTF_8), properties));
	}

	/**
	 * Waits until Menu consumed every message published before: the queue has a single consumer and keeps the
	 * order, so once a marker event for an unknown source is processed, so is everything sent before it.
	 */
	public void awaitDrained() {
		publish(UUID.randomUUID(), false);
	}

	private void awaitProcessed(String eventId) {
		Awaitility.await("Menu processes the availability event " + eventId).atMost(TIMEOUT)
				.pollInterval(Duration.ofMillis(50)).until(() -> processedEvents.existsById(eventId));
	}

	private static String payload(UUID sourceId, boolean available) {
		return "{\"sourceId\": \"" + sourceId + "\", \"isAvailable\": " + available + "}";
	}

}
