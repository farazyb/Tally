package com.example.walletservice.domain;

import jakarta.persistence.Embeddable;

import java.util.random.RandomGenerator;

/**
 * A 16-digit account number: the {@value #PREFIX} prefix, 9 random digits and a Luhn check digit.
 */
@Embeddable
public record AccountNumber(String value) {
    public static final String PREFIX = "621986";
    public static final int LENGTH = 16;
    private static final int RANDOM_DIGITS = LENGTH - PREFIX.length() - 1;

    public AccountNumber {
        if (value == null || value.length() != LENGTH || !isAsciiDigits(value)) {
            throw new IllegalArgumentException("Account number must be " + LENGTH + " digits: " + value);
        }
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Account number must start with " + PREFIX + ": " + value);
        }
        if (checkDigit(value.substring(0, LENGTH - 1)) != value.charAt(LENGTH - 1) - '0') {
            throw new IllegalArgumentException("Account number fails the Luhn check: " + value);
        }
    }

    /** Appends random digits to the prefix, then the Luhn check digit. Pass a SecureRandom outside tests. */
    public static AccountNumber generate(RandomGenerator random) {
        StringBuilder digits = new StringBuilder(LENGTH).append(PREFIX);
        for (int i = 0; i < RANDOM_DIGITS; i++) {
            digits.append(random.nextInt(10));
        }
        return new AccountNumber(digits.append(checkDigit(digits)).toString());
    }

    /** Luhn check digit for the payload. Doubling starts at the rightmost payload digit. */
    static int checkDigit(CharSequence payload) {
        int sum = 0;
        boolean doubled = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = payload.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return (10 - sum % 10) % 10;
    }

    // Character.isDigit would also accept non-ASCII digits such as '۶'.
    private static boolean isAsciiDigits(String value) {
        return value.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
