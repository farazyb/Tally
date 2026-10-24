package com.example.walletservice.domain;

/** The other party of a transfer. */
public record Counterparty(AccountNumber accountNumber, String name) {

    public Counterparty {
        if (accountNumber == null) {
            throw new IllegalArgumentException("Counterparty account number is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Counterparty name is required");
        }
    }
}
