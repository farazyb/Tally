package com.example.walletservice.integration.replies;

import java.util.UUID;

/** Reply to a command that was applied, or that had already been applied. */
public record CommandSucceeded(String owner, UUID operationId, long balanceAfterMinor, long walletVersion) {
}
