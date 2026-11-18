package com.fmatrestaurant.menu.infrastructure.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fmatrestaurant.menu.application.service.AvailabilityService;
import com.fmatrestaurant.menu.application.service.AvailabilityService.Outcome;
import com.fmatrestaurant.menu.infrastructure.dto.InventorySourceAvailabilityUpdated;
import com.fmatrestaurant.menu.infrastructure.rabbit.RabbitMqConfig;

/**
 * Applies the availability that Inventory communicates for each content source (RF-INTG-03). A message without
 * {@code sourceId} or {@code isAvailable} breaks the contract: it is rejected and ends in the dead-letter queue.
 */
@Component
public class InventoryAvailabilityListener {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryAvailabilityListener.class);

    private final AvailabilityService availability;

    public InventoryAvailabilityListener(AvailabilityService availability) {
        this.availability = availability;
    }

    @RabbitListener(queues = RabbitMqConfig.AVAILABILITY_QUEUE)
    public void onSourceAvailabilityUpdated(InventorySourceAvailabilityUpdated message,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (message.sourceId() == null || message.isAvailable() == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "An availability event requires sourceId and isAvailable: " + message);
        }
        String eventId = messageId != null && !messageId.isBlank() ? messageId : message.eventId();
        Outcome outcome = availability.apply(eventId, message.sourceId(), message.isAvailable());
        if (outcome == Outcome.UNKNOWN_SOURCE) {
            LOG.warn("Availability event for an unknown source: eventId={} sourceId={} isAvailable={}", eventId,
                    message.sourceId(), message.isAvailable());
        } else {
            LOG.debug("Availability event {}: eventId={} sourceId={} isAvailable={}", outcome, eventId,
                    message.sourceId(), message.isAvailable());
        }
    }
}
