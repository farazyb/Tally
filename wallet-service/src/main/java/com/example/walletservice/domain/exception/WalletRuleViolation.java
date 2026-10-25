package com.example.walletservice.domain.exception;

/** A wallet command broke a business rule. */
public abstract sealed class WalletRuleViolation extends RuntimeException
        permits InvalidAmountException, InsufficientFundsException, WalletFrozenException,
                WalletClosedException, DebitNotFoundException, OperationConflictException {

    protected WalletRuleViolation(String message) {
        super(message);
    }

    public abstract RejectionReason reason();
}
