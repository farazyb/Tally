package com.example.walletservice.domain.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A fact recorded in a wallet's event stream. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME)
public sealed interface WalletEvent
        permits WalletOpened, FundsDeposited, FundsDebited, FundsCredited,
                DebitReversed, WalletFrozen, WalletUnfrozen, WalletClosed {
}
