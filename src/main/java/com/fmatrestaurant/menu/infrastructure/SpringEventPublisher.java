package com.fmatrestaurant.menu.infrastructure;

import com.fmatrestaurant.menu.application.EventPublisher;
import com.fmatrestaurant.menu.domain.event.SourceEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(SourceEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
