package com.example.walletservice.domain.exception;

import com.example.walletservice.domain.Money;

public final class InvalidAmountException extends WalletRuleViolation {

    public InvalidAmountException(Money amount) {
        super("Amount must be positive, was " + amount.amountMinor() + " minor units");
    }

    @Override
    public RejectionReason reason() {
        return RejectionReason.INVALID_AMOUNT;
    }
}
