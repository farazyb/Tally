package com.example.walletservice.domain.exception;

import com.example.walletservice.domain.Money;

public final class OperationConflictException extends WalletRuleViolation {

    public OperationConflictException(String operationId, Money stored, Money received) {
        super("Operation " + operationId + " was applied with amount " + stored.amountMinor()
                + " and arrived again with " + received.amountMinor() + " (minor units)");
    }

    @Override
    public RejectionReason reason() {
        return RejectionReason.OPERATION_CONFLICT;
    }
}
