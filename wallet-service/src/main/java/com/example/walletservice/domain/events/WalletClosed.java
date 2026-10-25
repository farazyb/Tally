package com.example.walletservice.domain.events;

/** The wallet was closed. */
public record WalletClosed(String reason) implements WalletEvent {
}
