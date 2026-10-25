package com.example.walletservice.domain.exception;

public final class WalletClosedException extends WalletRuleViolation {

    public WalletClosedException(String owner) {
        super("Wallet of owner " + owner + " is closed");
    }

    @Override
    public RejectionReason reason() {
        return RejectionReason.WALLET_CLOSED;
    }
}
