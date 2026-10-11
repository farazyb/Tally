package com.example.walletservice.kafka.handlers;

import com.example.walletservice.kafka.EventHandler;
import com.example.walletservice.kafka.EventType;
import com.example.walletservice.kafka.events.ClientProfileCreatedEvent;
import com.example.walletservice.services.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@AllArgsConstructor
@Slf4j
public class UserCreationEventHandler implements EventHandler {
    private final WalletService walletService;
    private final JsonMapper jsonMapper;

    @Override
    public void handle(String eventId, String payload) {
        ClientProfileCreatedEvent event = jsonMapper.readValue(payload, ClientProfileCreatedEvent.class);
        if (event.subKey() == null || event.subKey().isBlank()) {
            throw new IllegalArgumentException("ClientProfileCreated event " + eventId + " has no subKey");
        }
        log.atDebug().addKeyValue("eventId", eventId).addKeyValue("subKey", event.subKey())
                .log("Handling ClientProfileCreated");
        walletService.createWallet(event.subKey(), eventId);
    }

    @Override
    public EventType getEventType() {
        return EventType.CLIENT_PROFILE_CREATED;
    }
}
