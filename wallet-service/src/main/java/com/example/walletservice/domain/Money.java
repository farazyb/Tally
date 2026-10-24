package com.example.walletservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

public record Money(long amountMinor) {
    public static final int FRACTION_DIGITS = 2;
    public static final Money ZERO = new Money(0);

    public Money plus(Money other) {
        return new Money(Math.addExact(amountMinor, other.amountMinor));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(amountMinor, other.amountMinor));
    }

    public boolean isLessThan(Money other) {
        return amountMinor < other.amountMinor;
    }

    @JsonIgnore
    public boolean isPositive() {
        return amountMinor > 0;
    }

    /** "30.00" → 3000. Rejects more than FRACTION_DIGITS decimals. */
    public static Money parse(String amount) {
        BigDecimal value = new BigDecimal(amount);
        if (value.scale() > FRACTION_DIGITS) {
            throw new IllegalArgumentException("Too many decimals: " + amount);
        }
        return new Money(value.movePointRight(FRACTION_DIGITS).longValueExact());
    }
}
