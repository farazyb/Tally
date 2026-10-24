package com.example.walletservice.domain.events;

import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.TransferId;

/** A debit was undone. */
public record DebitReversed(Money amount, TransferId transferId, String reason) implements WalletEvent {
}
