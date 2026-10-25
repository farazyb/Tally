package com.example.walletservice.kafka.commands;

import java.util.UUID;

/** Payload of the ReverseDebit command. */
public record ReverseDebit(String owner, UUID transferId, String reason) {
}
