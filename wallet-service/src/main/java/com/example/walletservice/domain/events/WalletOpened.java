package com.example.walletservice.domain.events;

import com.example.walletservice.domain.AccountNumber;

/** The wallet was opened for its owner. */
public record WalletOpened(String owner, AccountNumber accountNumber) implements WalletEvent {
}
