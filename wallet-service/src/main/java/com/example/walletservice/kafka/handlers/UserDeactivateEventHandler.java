package com.example.walletservice.kafka.handlers;

import com.example.walletservice.kafka.EventHandler;
import com.example.walletservice.kafka.EventType;
import org.springframework.stereotype.Component;

@Component
public class UserDeactivateEventHandler implements EventHandler {
    @Override
    public void handle(String eventId, String payload) {
        System.out.println("UserDeactivateEventHandler handle");
    }

    @Override
    public EventType getEventType() {
        return EventType.CLIENT_PROFILE_DELETED;
    }
}
