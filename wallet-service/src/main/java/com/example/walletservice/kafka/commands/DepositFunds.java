package com.example.walletservice.kafka.commands;

import java.util.UUID;

/** Payload of the DepositFunds command. */
public record DepositFunds(String owner, UUID topUpId, long amountMinor, String paymentReference) {
}
