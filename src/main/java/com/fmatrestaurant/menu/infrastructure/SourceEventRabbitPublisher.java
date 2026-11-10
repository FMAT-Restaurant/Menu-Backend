package com.fmatrestaurant.menu.infrastructure;

import com.fmatrestaurant.menu.domain.event.SourceEvent;
import com.fmatrestaurant.menu.infrastructure.dto.PlateDtoContract;
import com.fmatrestaurant.menu.infrastructure.dto.SourceDeletedContract;
import com.fmatrestaurant.menu.infrastructure.rabbit.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Publishes in {@code menu.topic} every {@link SourceEvent} once the transaction that produced it commits (or
 * right away if there is none), so a rolled back change is never announced. The network I/O runs outside the
 * request thread and a failure never reaches the caller: the change is already committed.
 *
 * <p>There is no outbox, so an event that cannot be delivered is lost; what this class guarantees is that the
 * loss is never silent. Every failure is logged with {@code eventId}, {@code type}, {@code sourceId} and
 * {@code routingKey}, enough to announce the source again by hand. Transient connection failures are retried
 * by the template ({@code spring.rabbitmq.template.retry.*}) and the broker's confirm is awaited, so a nack or a
 * missing confirm is reported too. An event that no queue receives is reported by
 * {@link RabbitMqConfig#logReturnedMessages()}.
 */
@Component
public class SourceEventRabbitPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(SourceEventRabbitPublisher.class);

    /** How long the broker may take to confirm a message before it is reported as unconfirmed. */
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitTemplate rabbitTemplate;
    private final Executor rabbitVirtualExecutor;

    public SourceEventRabbitPublisher(RabbitTemplate rabbitTemplate,
            @Qualifier("rabbitVirtualExecutor") Executor rabbitVirtualExecutor) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitVirtualExecutor = rabbitVirtualExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSourceEvent(SourceEvent event) {
        try {
            rabbitVirtualExecutor.execute(() -> send(event));
        } catch (RejectedExecutionException ex) {
            // Runs inside afterCommit: letting it escape would turn a committed request into a 500.
            logFailure("event NOT published, the publisher is shutting down", event, ex);
        }
    }

    void send(SourceEvent event) {
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.MENU_EXCHANGE, event.type().routingKey(), payload(event),
                    message -> {
                        var props = message.getMessageProperties();
                        props.setMessageId(event.eventId().toString());
                        props.setCorrelationId(event.sourceId());
                        props.setHeader("X-Event-Type", event.type().routingKey());
                        return message;
                    }, correlation);
        } catch (Exception ex) {
            logFailure("event NOT published, it could not be sent", event, ex);
            return;
        }
        correlation.getFuture()
                .orTimeout(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((confirm, error) -> confirmed(event, confirm, error));
    }

    /** What the broker answered: an ack, a nack, or nothing within {@link #CONFIRM_TIMEOUT}. */
    void confirmed(SourceEvent event, CorrelationData.Confirm confirm, Throwable error) {
        if (error != null) {
            logFailure("event not confirmed by the broker, it may be lost", event, error);
        } else if (!confirm.ack()) {
            LOG.error("Menu event NOT published, the broker rejected it ({}): {}", confirm.reason(), describe(event));
        } else {
            LOG.debug("Menu event confirmed by the broker: {}", describe(event));
        }
    }

    private static void logFailure(String what, SourceEvent event, Throwable cause) {
        LOG.error("Menu {}: {}", what, describe(event), cause);
    }

    /** The data needed to announce the source again by hand. */
    private static String describe(SourceEvent event) {
        return "eventId=" + event.eventId() + " type=" + event.type() + " sourceId=" + event.sourceId()
                + " routingKey=" + event.type().routingKey();
    }

    private static Object payload(SourceEvent event) {
        return event.type() == SourceEvent.Type.DELETED
                ? new SourceDeletedContract(event.sourceId())
                : new PlateDtoContract(event.sourceId(), event.name(), event.items());
    }
}
