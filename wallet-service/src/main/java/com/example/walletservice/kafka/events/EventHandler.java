package com.example.walletservice.kafka.events;

public interface EventHandler {

    void handle(String eventId, String payload);

    EventType getEventType();


}
