package com.example.walletservice.domain.events;

import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.TopUpId;

/** A confirmed payment was added to the balance. */
public record FundsDeposited(Money amount, TopUpId topUpId, PaymentReference paymentReference) implements WalletEvent {
}
