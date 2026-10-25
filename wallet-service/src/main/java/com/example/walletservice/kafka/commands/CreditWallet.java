package com.example.walletservice.kafka.commands;

import java.util.UUID;

/** Payload of the CreditWallet command: the second leg of a transfer. */
public record CreditWallet(String owner, UUID transferId, long amountMinor,
                           String counterpartyAccountNumber, String counterpartyName) {
}
