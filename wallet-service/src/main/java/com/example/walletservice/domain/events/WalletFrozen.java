package com.example.walletservice.domain.events;

/** The wallet was frozen. */
public record WalletFrozen(String reason) implements WalletEvent {
}
