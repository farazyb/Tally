package com.example.walletservice.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AccountNumberTest {

    @Test
    void checkDigitMatchesTheStandardLuhnExample() {
        // 79927398713 is the reference Luhn-valid number.
        assertThat(AccountNumber.checkDigit("7992739871")).isEqualTo(3);
    }

    @Test
    void acceptsAValidNumber() {
        assertThat(new AccountNumber("6219861234567898").value()).isEqualTo("6219861234567898");
    }

    @Test
    void generatedNumbersHaveThePrefixLengthAndAValidCheckDigit() {
        Random random = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            String value = AccountNumber.generate(random).value();

            assertThat(value).hasSize(AccountNumber.LENGTH).startsWith(AccountNumber.PREFIX);
            assertThat(new AccountNumber(value)).isNotNull();
        }
    }

    @Test
    void sameSeedGeneratesTheSameNumber() {
        assertThat(AccountNumber.generate(new Random(7))).isEqualTo(AccountNumber.generate(new Random(7)));
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountNumber("6219861234567890"))
                .withMessageContaining("Luhn");
    }

    @Test
    void rejectsAnotherPrefixEvenWhenLuhnIsValid() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountNumber("4111111111111111"))
                .withMessageContaining(AccountNumber.PREFIX);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "621986123456789", "62198612345678988", "62198612345678a8", "621986123456789۸"})
    void rejectsValuesThatAreNotSixteenAsciiDigits(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountNumber(value))
                .withMessageContaining("16 digits");
    }
}
