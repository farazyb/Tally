package com.example.walletservice.domain.events;

import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.TransferId;

/** Money entered the wallet as the second leg of a transfer. */
public record FundsCredited(Money amount, TransferId transferId, Counterparty counterparty) implements WalletEvent {
}
