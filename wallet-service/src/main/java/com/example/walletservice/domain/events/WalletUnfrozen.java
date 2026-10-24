package com.example.walletservice.domain.events;

/** A frozen wallet is active again. */
public record WalletUnfrozen(String reason) implements WalletEvent {
}
