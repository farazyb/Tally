package com.example.walletservice.kafka;

public interface EventHandler {

    void handle(String eventId, String payload);

    EventType getEventType();


}
