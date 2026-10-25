package com.example.walletservice.integration.events;

import java.time.Instant;
import java.util.UUID;

public record FundsDepositedV1(UUID eventId, int schemaVersion, String owner, String accountNumber, long walletVersion,
                             Instant occurredAt,
                             long amountMinor, long balanceAfterMinor, UUID topUpId, String paymentReference) implements WalletIntegrationEvent {
}
