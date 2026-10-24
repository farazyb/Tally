package com.example.walletservice.domain;

import java.util.UUID;

/** Identifies one transfer. */
public record TransferId(UUID value) {

    public TransferId {
        if (value == null) {
            throw new IllegalArgumentException("Transfer id is required");
        }
    }
}
