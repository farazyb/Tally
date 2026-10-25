package com.example.walletservice.kafka.commands;

import java.util.Arrays;
import java.util.Optional;

public enum CommandType {
    DEBIT_WALLET("DebitWallet"),
    CREDIT_WALLET("CreditWallet"),
    REVERSE_DEBIT("ReverseDebit"),
    DEPOSIT_FUNDS("DepositFunds"),
    CLOSE_WALLET("CloseWallet");
    private final String value;

    CommandType(String value) {
        this.value = value;
    }

    /** The commandType header value, for example "DebitWallet". */
    public String value() {
        return value;
    }

    public static Optional<CommandType> from(String v) {
        return Arrays.stream(values()).filter(t -> t.value.equals(v)).findFirst();
    }
}
