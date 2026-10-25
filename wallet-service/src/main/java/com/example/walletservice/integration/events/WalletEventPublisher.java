package com.example.walletservice.integration.events;

import com.example.common.outbox.OutboxWriter;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.domain.WalletEventEntry;
import com.example.walletservice.domain.events.DebitReversed;
import com.example.walletservice.domain.events.FundsCredited;
import com.example.walletservice.domain.events.FundsDebited;
import com.example.walletservice.domain.events.FundsDeposited;
import com.example.walletservice.domain.events.WalletClosed;
import com.example.walletservice.domain.events.WalletFrozen;
import com.example.walletservice.domain.events.WalletOpened;
import com.example.walletservice.domain.events.WalletUnfrozen;
import com.example.walletservice.integration.OutboxChannel;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes the wallet's new events to the outbox as integration events on wallet.events. Must run in the transaction
 * that appends the events. Every command raises at most one event, so balanceAfterMinor is the wallet's balance.
 */
@Component
@AllArgsConstructor
public class WalletEventPublisher {
    private final OutboxWriter outboxWriter;

    public void publishNew(Wallet wallet) {
        for (WalletEventEntry entry : wallet.newEntries()) {
            outboxWriter.write(OutboxChannel.EVENTS, wallet.owner(), entry.type(), toIntegrationEvent(wallet, entry));
        }
    }

    private static WalletIntegrationEvent toIntegrationEvent(Wallet wallet, WalletEventEntry entry) {
        UUID eventId = entry.eventId();
        int schema = WalletIntegrationEvent.SCHEMA_VERSION;
        String owner = wallet.owner();
        String account = wallet.accountNumber().value();
        long version = entry.version();
        Instant occurredAt = entry.occurredAt();
        long balanceAfter = wallet.balance().amountMinor();
        return switch (entry.payload()) {
            case WalletOpened _ -> new WalletOpenedV1(eventId, schema, owner, account, version, occurredAt);
            case FundsDeposited e -> new FundsDepositedV1(eventId, schema, owner, account, version, occurredAt,
                    e.amount().amountMinor(), balanceAfter, e.topUpId().value(), e.paymentReference().value());
            case FundsDebited e -> new FundsDebitedV1(eventId, schema, owner, account, version, occurredAt,
                    e.amount().amountMinor(), balanceAfter, e.transferId().value(),
                    e.counterparty().accountNumber().value(), e.counterparty().name());
            case FundsCredited e -> new FundsCreditedV1(eventId, schema, owner, account, version, occurredAt,
                    e.amount().amountMinor(), balanceAfter, e.transferId().value(),
                    e.counterparty().accountNumber().value(), e.counterparty().name());
            case DebitReversed e -> new DebitReversedV1(eventId, schema, owner, account, version, occurredAt,
                    e.amount().amountMinor(), balanceAfter, e.transferId().value(), e.reason());
            case WalletFrozen e -> new WalletFrozenV1(eventId, schema, owner, account, version, occurredAt, e.reason());
            case WalletUnfrozen e -> new WalletUnfrozenV1(eventId, schema, owner, account, version, occurredAt, e.reason());
            case WalletClosed e -> new WalletClosedV1(eventId, schema, owner, account, version, occurredAt, e.reason());
        };
    }
}
