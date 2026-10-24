package com.example.walletservice.domain.exception;

import com.example.walletservice.domain.TransferId;

public final class DebitNotFoundException extends WalletRuleViolation {

    public DebitNotFoundException(TransferId transferId) {
        super("No debit for transfer " + transferId.value());
    }

    @Override
    public RejectionReason reason() {
        return RejectionReason.DEBIT_NOT_FOUND;
    }
}
