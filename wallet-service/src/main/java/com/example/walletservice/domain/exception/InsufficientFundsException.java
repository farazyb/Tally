package com.example.walletservice.domain.exception;

import com.example.walletservice.domain.Money;

public final class InsufficientFundsException extends WalletRuleViolation {

    public InsufficientFundsException(Money amount, Money balance) {
        super("Amount " + amount.amountMinor() + " exceeds the balance " + balance.amountMinor() + " (minor units)");
    }

    @Override
    public RejectionReason reason() {
        return RejectionReason.INSUFFICIENT_FUNDS;
    }
}
