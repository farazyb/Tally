package com.example.walletservice.integration.events;

import java.time.Instant;
import java.util.UUID;

/** A wallet fact published on wallet.events for other services. */
public sealed interface WalletIntegrationEvent
        permits WalletOpenedV1, FundsDepositedV1, FundsDebitedV1, FundsCreditedV1,
                DebitReversedV1, WalletFrozenV1, WalletUnfrozenV1, WalletClosedV1 {
    int SCHEMA_VERSION = 1;

    UUID eventId();

    int schemaVersion();

    String owner();

    String accountNumber();

    long walletVersion();

    Instant occurredAt();
}
