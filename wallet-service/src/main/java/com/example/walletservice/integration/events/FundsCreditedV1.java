package com.example.walletservice.integration.events;

import java.time.Instant;
import java.util.UUID;

public record FundsCreditedV1(UUID eventId, int schemaVersion, String owner, String accountNumber, long walletVersion,
                             Instant occurredAt,
                             long amountMinor, long balanceAfterMinor, UUID transferId,
                             String counterpartyAccountNumber, String counterpartyName) implements WalletIntegrationEvent {
}
