package com.fmatrestaurant.menu;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ 4.3.6 para pruebas. Se importa solo en las pruebas que necesitan el broker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitMqTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitMqContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.6"));
	}

}
