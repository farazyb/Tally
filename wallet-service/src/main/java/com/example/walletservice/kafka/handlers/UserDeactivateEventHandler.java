package com.example.walletservice.kafka.handlers;

import com.example.walletservice.kafka.EventHandler;
import com.example.walletservice.kafka.EventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserDeactivateEventHandler implements EventHandler {
    @Override
    public void handle(String eventId, String payload) {
        log.warn("Event {} of type {} received but not handled yet", eventId, getEventType());
    }

    @Override
    public EventType getEventType() {
        return EventType.CLIENT_PROFILE_DELETED;
    }
}
