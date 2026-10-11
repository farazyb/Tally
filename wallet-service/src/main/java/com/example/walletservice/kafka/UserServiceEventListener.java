package com.example.walletservice.kafka;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes user-service events (published by Debezium from its outbox):
 * the "eventType" header selects the handler, the "id" header is the event id.
 */
@Component
@AllArgsConstructor
@Slf4j
public class UserServiceEventListener {
    private final List<EventHandler> handlers;

    @KafkaListener(topics = "user.client-events")
    public void listen(@Payload String data,
                       @Header("eventType") String eventType,
                       @Header("id") String eventId) {
        log.info("Received event {} of type {}", eventId, eventType);
        EventType.from(eventType)
                .flatMap(type -> handlers.stream()
                        .filter(h -> h.getEventType() == type)
                        .findFirst())
                .ifPresentOrElse(h -> h.handle(eventId, data),
                        () -> log.debug("Ignoring event type {}", eventType));
    }
}
