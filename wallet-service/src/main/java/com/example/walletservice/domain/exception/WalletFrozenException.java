package com.example.walletservice.domain.exception;

public final class WalletFrozenException extends WalletRuleViolation {

    public WalletFrozenException(String owner) {
        super("Wallet of owner " + owner + " is frozen");
    }

    @Override
    public RejectionReason reason() {
        return RejectionReason.WALLET_FROZEN;
    }
}
