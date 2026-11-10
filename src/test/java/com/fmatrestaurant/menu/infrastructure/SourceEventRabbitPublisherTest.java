package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fmatrestaurant.menu.domain.event.InventoryItemDto;
import com.fmatrestaurant.menu.domain.event.SourceEvent;
import com.fmatrestaurant.menu.infrastructure.dto.PlateDtoContract;
import com.fmatrestaurant.menu.infrastructure.dto.SourceDeletedContract;

class SourceEventRabbitPublisherTest {

	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

	private final SourceEventRabbitPublisher publisher = new SourceEventRabbitPublisher(rabbitTemplate,
			Runnable::run);

	private Object[] sent(String routingKey) {
		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<MessagePostProcessor> post = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(rabbitTemplate).convertAndSend(eq("menu.topic"), eq(routingKey), payload.capture(), post.capture(),
				any(CorrelationData.class));
		return new Object[] {payload.getValue(), post.getValue()};
	}

	@Test
	void publishesCreationsAndUpdatesInMenuTopicWithTheirPlatePayload() {
		UUID id = UUID.randomUUID();
		List<InventoryItemDto> items = List.of(new InventoryItemDto("INV-1", new BigDecimal("2")));

		publisher.onSourceEvent(SourceEvent.created(id, "Burger", items));

		Object[] sent = sent("menu.source.created");
		assertEquals(new PlateDtoContract(id.toString(), "Burger", items), sent[0]);
	}

	@Test
	void publishesUpdatesWithTheUpdatedRoutingKey() {
		UUID id = UUID.randomUUID();

		publisher.onSourceEvent(SourceEvent.updated(id, null, List.of()));

		assertEquals(new PlateDtoContract(id.toString(), null, List.of()), sent("menu.source.updated")[0]);
	}

	@Test
	void publishesDeletionsWithOnlyTheSourceId() {
		UUID id = UUID.randomUUID();

		publisher.onSourceEvent(SourceEvent.deleted(id));

		assertEquals(new SourceDeletedContract(id.toString()), sent("menu.source.deleted")[0]);
	}

	@Test
	void identifiesTheMessageByTheEventAndCorrelatesItWithTheSource() throws Exception {
		SourceEvent event = SourceEvent.deleted(UUID.randomUUID());
		publisher.onSourceEvent(event);

		MessagePostProcessor post = (MessagePostProcessor) sent("menu.source.deleted")[1];
		Message message = post.postProcessMessage(new Message(new byte[0], new MessageProperties()));

		assertEquals(event.eventId().toString(), message.getMessageProperties().getMessageId());
		assertEquals(event.sourceId(), message.getMessageProperties().getCorrelationId());
		assertEquals("menu.source.deleted", message.getMessageProperties().getHeader("X-Event-Type"));
	}

	@Test
	void aBrokerFailureIsLoggedAndDoesNotPropagate() {
		doThrow(new IllegalStateException("down")).when(rabbitTemplate)
				.convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class),
						any(CorrelationData.class));

		publisher.onSourceEvent(SourceEvent.deleted(UUID.randomUUID()));
	}

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger publisherLog = (Logger) LoggerFactory.getLogger(SourceEventRabbitPublisher.class);

	@BeforeEach
	void captureLogs() {
		publisherLog.setLevel(Level.DEBUG);
		logs.start();
		publisherLog.addAppender(logs);
	}

	@AfterEach
	void releaseLogs() {
		publisherLog.detachAppender(logs);
	}

	private List<String> errors() {
		return logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).map(ILoggingEvent::getFormattedMessage)
				.toList();
	}

	@Test
	void aSendFailureIsLoggedWithWhatIsNeededToAnnounceTheSourceAgain() {
		SourceEvent event = SourceEvent.created(UUID.randomUUID(), "Burger", List.of());
		doThrow(new IllegalStateException("down")).when(rabbitTemplate)
				.convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class),
						any(CorrelationData.class));

		publisher.onSourceEvent(event);

		assertEquals(1, errors().size());
		String error = errors().get(0);
		assertTrue(error.contains("NOT published"), error);
		assertTrue(error.contains("eventId=" + event.eventId()), error);
		assertTrue(error.contains("sourceId=" + event.sourceId()), error);
		assertTrue(error.contains("routingKey=menu.source.created"), error);
	}

	@Test
	void aNackIsLoggedAsAnError() {
		SourceEvent event = SourceEvent.deleted(UUID.randomUUID());

		publisher.confirmed(event, new CorrelationData.Confirm(false, "queue limit"), null);

		assertEquals(1, errors().size());
		assertTrue(errors().get(0).contains("rejected it (queue limit)"), errors().get(0));
		assertTrue(errors().get(0).contains("sourceId=" + event.sourceId()), errors().get(0));
	}

	@Test
	void anAckIsNotAnError() {
		publisher.confirmed(SourceEvent.deleted(UUID.randomUUID()), new CorrelationData.Confirm(true, null), null);

		assertEquals(List.of(), errors());
	}

	@Test
	void aMissingConfirmIsLoggedAsAnError() {
		publisher.confirmed(SourceEvent.deleted(UUID.randomUUID()), null, new TimeoutException());

		assertEquals(1, errors().size());
		assertTrue(errors().get(0).contains("not confirmed by the broker"), errors().get(0));
	}

	@Test
	void aRejectedExecutionDoesNotBreakTheCommittedRequest() {
		SourceEventRabbitPublisher closed = new SourceEventRabbitPublisher(rabbitTemplate, task -> {
			throw new RejectedExecutionException("shutdown");
		});

		assertDoesNotThrow(() -> closed.onSourceEvent(SourceEvent.deleted(UUID.randomUUID())));
		assertEquals(1, errors().size());
		assertTrue(errors().get(0).contains("shutting down"), errors().get(0));
	}
}
