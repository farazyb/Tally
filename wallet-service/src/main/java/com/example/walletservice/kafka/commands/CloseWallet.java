package com.example.walletservice.kafka.commands;

import java.util.UUID;

/** Payload of the CloseWallet command, sent by user-service. */
public record CloseWallet(String owner, UUID requestId, String reason) {
}
