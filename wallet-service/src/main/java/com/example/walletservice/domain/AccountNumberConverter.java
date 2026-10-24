package com.example.walletservice.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores an {@link AccountNumber} as its 16-digit string in a single column. */
@Converter(autoApply = true)
public class AccountNumberConverter implements AttributeConverter<AccountNumber, String> {

    @Override
    public String convertToDatabaseColumn(AccountNumber accountNumber) {
        return accountNumber == null ? null : accountNumber.value();
    }

    @Override
    public AccountNumber convertToEntityAttribute(String value) {
        return value == null ? null : new AccountNumber(value);
    }
}
