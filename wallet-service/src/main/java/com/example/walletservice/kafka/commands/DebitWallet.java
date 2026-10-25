package com.example.walletservice.kafka.commands;

import java.util.UUID;

/** Payload of the DebitWallet command: the first leg of a transfer. */
public record DebitWallet(String owner, UUID transferId, long amountMinor,
                          String counterpartyAccountNumber, String counterpartyName) {
}
