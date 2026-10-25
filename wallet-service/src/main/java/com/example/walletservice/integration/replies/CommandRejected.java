package com.example.walletservice.integration.replies;

import com.example.walletservice.domain.exception.RejectionReason;

import java.util.UUID;

/** Reply to a command that broke a wallet rule. */
public record CommandRejected(String owner, UUID operationId, RejectionReason reason, String message) {
}
