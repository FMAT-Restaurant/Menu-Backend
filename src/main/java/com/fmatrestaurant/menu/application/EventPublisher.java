package com.fmatrestaurant.menu.application;

import com.fmatrestaurant.menu.domain.event.SourceEvent;

public interface EventPublisher {
    void publish(SourceEvent event);
}
