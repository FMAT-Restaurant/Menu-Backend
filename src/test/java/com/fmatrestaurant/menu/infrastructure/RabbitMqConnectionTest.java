package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fmatrestaurant.menu.RabbitMqTestcontainersConfiguration;
import com.fmatrestaurant.menu.TestcontainersConfiguration;

/**
 * Comprueba que el backend puede conectarse a RabbitMQ. No declara exchanges,
 * colas ni bindings. Requiere Docker.
 */
@Import({ TestcontainersConfiguration.class, RabbitMqTestcontainersConfiguration.class })
@SpringBootTest
class RabbitMqConnectionTest {

	@Autowired
	private ConnectionFactory connectionFactory;

	@Test
	void connectsToBroker() {
		try (Connection connection = connectionFactory.createConnection()) {
			assertTrue(connection.isOpen());
		}
	}

}
