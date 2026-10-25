package com.example.walletservice.domain.events;

import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.TransferId;

/** Money left the wallet as the first leg of a transfer. */
public record FundsDebited(Money amount, TransferId transferId, Counterparty counterparty) implements WalletEvent {
}
