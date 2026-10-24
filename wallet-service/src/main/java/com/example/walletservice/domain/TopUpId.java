package com.example.walletservice.domain;

import java.util.UUID;

/** Identifies one top-up. */
public record TopUpId(UUID value) {

    public TopUpId {
        if (value == null) {
            throw new IllegalArgumentException("Top-up id is required");
        }
    }
}
