package com.fmatrestaurant.menu.infrastructure.listener;

import com.fmatrestaurant.menu.infrastructure.rabbit.RabbitMqConfig;
import com.fmatrestaurant.menu.infrastructure.dto.InventorySourceAvailabilityChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockAvailabilityListener {


    private static final Logger LOG = LoggerFactory.getLogger(StockAvailabilityListener.class);

    @RabbitListener(queues = RabbitMqConfig.AVAILABILITY_QUEUE)
    public void onInventoryItemOfferedAvailabilityChanged(InventorySourceAvailabilityChanged message) {
        LOG.info("Received availability event: {}", message);
    }
}
