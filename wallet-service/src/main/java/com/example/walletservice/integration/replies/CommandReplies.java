package com.example.walletservice.integration.replies;

import com.example.common.outbox.OutboxWriter;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.domain.exception.RejectionReason;
import com.example.walletservice.domain.exception.WalletRuleViolation;
import com.example.walletservice.integration.OutboxChannel;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Writes the replies to wallet commands to the outbox, for wallet.replies. Must run in the command's transaction. */
@Component
@AllArgsConstructor
public class CommandReplies {
    private final OutboxWriter outboxWriter;

    public void succeeded(String commandType, Wallet wallet, UUID operationId) {
        outboxWriter.write(OutboxChannel.REPLIES, wallet.owner(), commandType + "Succeeded",
                new CommandSucceeded(wallet.owner(), operationId, wallet.balance().amountMinor(), wallet.version()));
    }

    public void rejected(String commandType, String owner, UUID operationId, WalletRuleViolation violation) {
        rejected(commandType, owner, operationId, violation.reason(), violation.getMessage());
    }

    public void walletNotFound(String commandType, String owner, UUID operationId) {
        rejected(commandType, owner, operationId, RejectionReason.WALLET_NOT_FOUND, "No wallet for owner " + owner);
    }

    private void rejected(String commandType, String owner, UUID operationId, RejectionReason reason, String message) {
        outboxWriter.write(OutboxChannel.REPLIES, owner, commandType + "Rejected",
                new CommandRejected(owner, operationId, reason, message));
    }
}
