package com.example.walletservice.integration.events;

import java.time.Instant;
import java.util.UUID;

public record WalletClosedV1(UUID eventId, int schemaVersion, String owner, String accountNumber, long walletVersion,
                             Instant occurredAt,
                             String reason) implements WalletIntegrationEvent {
}
