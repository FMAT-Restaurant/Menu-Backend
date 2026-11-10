package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fmatrestaurant.menu.RabbitMqTestcontainersConfiguration;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.infrastructure.dto.SourceDeletedContract;
import com.fmatrestaurant.menu.infrastructure.rabbit.RabbitMqConfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * What happens when RabbitMQ does not behave: a message nobody receives is reported, and an inbound message that
 * cannot be processed ends in the dead-letter queue together with its cause. Requires Docker.
 */
@Import({ TestcontainersConfiguration.class, RabbitMqTestcontainersConfiguration.class })
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.rabbitmq.listener.simple.auto-startup=true",
		"spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
		"spring.rabbitmq.listener.simple.retry.max-interval=100ms" })
class RabbitErrorHandlingIntegrationTest {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Value("${menu.rabbitmq.inventory.availability-routing-key}")
	private String availabilityRoutingKey;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger configLog = (Logger) LoggerFactory.getLogger(RabbitMqConfig.class);

	@BeforeEach
	void setUp() {
		amqpAdmin.purgeQueue(RabbitMqConfig.AVAILABILITY_DLQ, true);
		logs.start();
		configLog.addAppender(logs);
	}

	@AfterEach
	void tearDown() {
		configLog.detachAppender(logs);
	}

	@Test
	void anEventThatNoQueueReceivesIsReportedNotSilentlyDropped() throws InterruptedException {
		UUID sourceId = UUID.randomUUID();

		rabbitTemplate.convertAndSend(RabbitMqConfig.MENU_EXCHANGE, "menu.source.nobody-listens",
				new SourceDeletedContract(sourceId.toString()));

		long deadline = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < deadline && logs.list.isEmpty()) {
			TimeUnit.MILLISECONDS.sleep(50);
		}
		assertEquals(1, logs.list.size());
		String warning = logs.list.get(0).getFormattedMessage();
		assertTrue(warning.contains("not routed to any queue"), warning);
		assertTrue(warning.contains("routingKey=menu.source.nobody-listens"), warning);
	}

	@Test
	void aMessageThatCannotBeReadEndsInTheDeadLetterQueueWithItsCause() {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		Message broken = new Message("{not json".getBytes(StandardCharsets.UTF_8), properties);

		rabbitTemplate.send(RabbitMqConfig.INVENTORY_EXCHANGE, availabilityRoutingKey, broken);

		Message dead = rabbitTemplate.receive(RabbitMqConfig.AVAILABILITY_DLQ, 15_000);
		assertNotNull(dead, "the message must reach the dead-letter queue");
		assertEquals("{not json", new String(dead.getBody(), StandardCharsets.UTF_8));
		assertNotNull(dead.getMessageProperties().getHeader("x-exception-message"),
				() -> "headers: " + dead.getMessageProperties().getHeaders().keySet());
		assertEquals(RabbitMqConfig.INVENTORY_EXCHANGE, dead.getMessageProperties().getHeader("x-original-exchange"));
	}
}
