package com.example.walletservice.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ValueObjectValidationTest {
    private static final AccountNumber ACCOUNT = AccountNumber.generate(new Random(1));

    @Test
    void transferIdAndTopUpIdRequireAValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TransferId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TopUpId(null));
        UUID value = UUID.randomUUID();
        assertThat(new TransferId(value).value()).isEqualTo(value);
        assertThat(new TopUpId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void paymentReferenceMustNotBeBlank(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentReference(value));
    }

    @Test
    void counterpartyRequiresAnAccountNumber() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Counterparty(null, "Jane Doe"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void counterpartyNameMustNotBeBlank(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Counterparty(ACCOUNT, name));
    }

    @Test
    void validValuesAreKept() {
        assertThat(new PaymentReference("p1").value()).isEqualTo("p1");
        assertThat(new Counterparty(ACCOUNT, "Jane Doe").name()).isEqualTo("Jane Doe");
    }
}
