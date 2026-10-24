package com.example.walletservice.domain;

/** The payment provider's reference for a payment. */
public record PaymentReference(String value) {

    public PaymentReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required");
        }
    }
}
