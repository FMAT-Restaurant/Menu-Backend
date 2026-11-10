package com.fmatrestaurant.menu.infrastructure.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Topología de RabbitMQ de Menu.
 * <ul>
 *   <li>{@value #MENU_EXCHANGE}: exchange propio donde Menu publica sus eventos.</li>
 *   <li>{@value #INVENTORY_EXCHANGE}: exchange de Inventario; Menu enlaza su cola de disponibilidad a él.</li>
 *   <li>{@value #DLX_EXCHANGE}: dead-letter exchange propio de Menu para los mensajes rechazados.</li>
 * </ul>
 */
@Configuration
public class RabbitMqConfig {

    public static final String MENU_EXCHANGE = "menu.topic";
    public static final String INVENTORY_EXCHANGE = "inventory.topic";
    public static final String DLX_EXCHANGE = "menu.dlx";
    public static final String AVAILABILITY_QUEUE = "menu.inventory.availability.queue";
    public static final String AVAILABILITY_DLQ = "menu.inventory.availability.dlq";

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMqConfig.class);

    // Spring Boot inyecta este converter en RabbitTemplate y en @RabbitListener
    @Bean
    public MessageConverter amqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // Executor de SourceEventRabbitPublisher; los listeners usan el de Spring Boot (hilos virtuales)
    @Bean(name = "rabbitVirtualExecutor")
    public Executor rabbitVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // ---------- Salida: eventos de Menu ----------

    @Bean
    public TopicExchange menuExchange() {
        return new TopicExchange(MENU_EXCHANGE, true, false);
    }

    /**
     * Mensajes que el broker no pudo entregar a ninguna cola (nadie enlazado a la routing key). Requiere
     * {@code publisher-returns} y {@code template.mandatory}. Es una advertencia: sin consumidores suscritos a
     * {@code menu.source.*} es una condición operativa, no un fallo de Menu.
     */
    @Bean
    public RabbitTemplateCustomizer logReturnedMessages() {
        return template -> template.setReturnsCallback(returned -> LOG.warn(
                "Menu event not routed to any queue ({}): messageId={} exchange={} routingKey={}",
                returned.getReplyText(), returned.getMessage().getMessageProperties().getMessageId(),
                returned.getExchange(), returned.getRoutingKey()));
    }

    // ---------- Entrada: disponibilidad publicada por Inventario ----------

    /**
     * Cuando se agotan los reintentos del listener, republica el mensaje en la DLQ con la causa en la
     * cabecera {@code x-exception-message} (y la traza en {@code x-exception-stacktrace} si cabe en el frame);
     * Spring Boot lo engancha solo.
     */
    @Bean
    public MessageRecoverer availabilityMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, DLX_EXCHANGE, AVAILABILITY_DLQ);
    }

    /**
     * Inventario es el dueño de este exchange. Declararlo aquí es idempotente (mismo tipo y durabilidad)
     * y permite crear el binding aunque Menu arranque antes que Inventario.
     */
    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(INVENTORY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue availabilityDeadLetterQueue() {
        return QueueBuilder.durable(AVAILABILITY_DLQ).build();
    }

    @Bean
    public Binding availabilityDlqBinding(Queue availabilityDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(availabilityDeadLetterQueue)
                .to(deadLetterExchange)
                .with(AVAILABILITY_DLQ);
    }

    @Bean
    public Queue availabilityQueue() {
        return QueueBuilder.durable(AVAILABILITY_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", AVAILABILITY_DLQ)
                .build();
    }

    /** Enlaza la cola de disponibilidad a {@code inventory.topic} con la routing key de Inventario. */
    @Bean
    public Binding availabilityQueueBinding(
            Queue availabilityQueue,
            TopicExchange inventoryExchange,
            @Value("${menu.rabbitmq.inventory.availability-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(availabilityQueue)
                .to(inventoryExchange)
                .with(routingKey);
    }
}
