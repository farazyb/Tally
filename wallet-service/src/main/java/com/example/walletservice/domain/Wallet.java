package com.example.walletservice.domain;

import com.example.walletservice.domain.events.DebitReversed;
import com.example.walletservice.domain.events.FundsCredited;
import com.example.walletservice.domain.events.FundsDebited;
import com.example.walletservice.domain.events.FundsDeposited;
import com.example.walletservice.domain.events.WalletClosed;
import com.example.walletservice.domain.events.WalletEvent;
import com.example.walletservice.domain.events.WalletFrozen;
import com.example.walletservice.domain.events.WalletOpened;
import com.example.walletservice.domain.events.WalletUnfrozen;
import com.example.walletservice.domain.exception.DebitNotFoundException;
import com.example.walletservice.domain.exception.InsufficientFundsException;
import com.example.walletservice.domain.exception.InvalidAmountException;
import com.example.walletservice.domain.exception.OperationConflictException;
import com.example.walletservice.domain.exception.WalletClosedException;
import com.example.walletservice.domain.exception.WalletFrozenException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The wallet aggregate. Its state is rebuilt from its events. */
@Entity
@Table(name = "wallet")
public class Wallet {
    public static final String ACCOUNT_NUMBER_CONSTRAINT = "uk_wallet_account_number";

    @Id
    @SequenceGenerator(
            name = "wallet_id_seq",
            sequenceName = "wallet_sequence",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "wallet_id_seq")
    private Long id;

    // One wallet per client.
    @Column(name = "owner", nullable = false, updatable = false)
    private String owner;

    // Id of the event that created this wallet (Debezium "id" header).
    @Column(name = "creation_request_id", nullable = false, updatable = false)
    private String creationRequestId;

    @Convert(converter = AccountNumberConverter.class)
    @Column(name = "account_number", nullable = false, updatable = false)
    private AccountNumber accountNumber;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.PERSIST)
    @OrderBy("version")
    private List<WalletEventEntry> events = new ArrayList<>();

    @Transient
    private boolean replayed;
    @Transient
    private int replayedEntries;
    @Transient
    private Status status;
    @Transient
    private Money balance;
    @Transient
    private Map<TransferId, Debit> debits;
    @Transient
    private Map<TransferId, Money> credits;
    @Transient
    private Map<PaymentReference, Money> deposits;

    protected Wallet() {
        // for JPA
    }

    private Wallet(String owner, String creationRequestId, AccountNumber accountNumber) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner is required");
        }
        if (creationRequestId == null || creationRequestId.isBlank()) {
            throw new IllegalArgumentException("Creation request id is required");
        }
        if (accountNumber == null) {
            throw new IllegalArgumentException("Account number is required");
        }
        this.owner = owner;
        this.creationRequestId = creationRequestId;
        this.accountNumber = accountNumber;
    }

    /** Creates a new wallet. Its first event is {@link WalletOpened}. */
    public static Wallet open(String owner, String creationRequestId, AccountNumber accountNumber) {
        Wallet wallet = new Wallet(owner, creationRequestId, accountNumber);
        wallet.ensureReplayed();
        wallet.raise(new WalletOpened(owner, accountNumber));
        return wallet;
    }

    /** For tests: a wallet with the given history, not yet replayed. */
    static Wallet fromHistory(String owner, String creationRequestId, AccountNumber accountNumber,
                              List<WalletEvent> history) {
        Wallet wallet = new Wallet(owner, creationRequestId, accountNumber);
        for (WalletEvent event : history) {
            wallet.events.add(new WalletEventEntry(wallet, wallet.events.size() + 1, event));
        }
        return wallet;
    }

    public Optional<FundsDeposited> deposit(Money amount, TopUpId topUpId, PaymentReference paymentReference) {
        ensureReplayed();
        if (alreadyApplied(deposits.get(paymentReference), amount, paymentReference.value())) {
            return Optional.empty();
        }
        requirePositive(amount);
        requireNotClosed();
        return Optional.of(raise(new FundsDeposited(amount, topUpId, paymentReference)));
    }

    public Optional<FundsDebited> debit(Money amount, TransferId transferId, Counterparty counterparty) {
        ensureReplayed();
        Debit existing = debits.get(transferId);
        if (alreadyApplied(existing == null ? null : existing.amount(), amount, transferId.value().toString())) {
            return Optional.empty();
        }
        requirePositive(amount);
        if (status == Status.FROZEN) {
            throw new WalletFrozenException(owner);
        }
        requireNotClosed();
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(amount, balance);
        }
        return Optional.of(raise(new FundsDebited(amount, transferId, counterparty)));
    }

    public Optional<FundsCredited> credit(Money amount, TransferId transferId, Counterparty counterparty) {
        ensureReplayed();
        if (alreadyApplied(credits.get(transferId), amount, transferId.value().toString())) {
            return Optional.empty();
        }
        requirePositive(amount);
        requireNotClosed();
        return Optional.of(raise(new FundsCredited(amount, transferId, counterparty)));
    }

    public Optional<DebitReversed> reverseDebit(TransferId transferId, String reason) {
        ensureReplayed();
        Debit debit = debits.get(transferId);
        if (debit == null) {
            throw new DebitNotFoundException(transferId);
        }
        if (debit.reversed()) {
            return Optional.empty();
        }
        return Optional.of(raise(new DebitReversed(debit.amount(), transferId, reason)));
    }

    public Optional<WalletFrozen> freeze(String reason) {
        ensureReplayed();
        if (status == Status.FROZEN) {
            return Optional.empty();
        }
        requireNotClosed();
        return Optional.of(raise(new WalletFrozen(reason)));
    }

    public Optional<WalletUnfrozen> unfreeze(String reason) {
        ensureReplayed();
        if (status == Status.ACTIVE) {
            return Optional.empty();
        }
        requireNotClosed();
        return Optional.of(raise(new WalletUnfrozen(reason)));
    }

    public Optional<WalletClosed> close(String reason) {
        ensureReplayed();
        if (status == Status.CLOSED) {
            return Optional.empty();
        }
        return Optional.of(raise(new WalletClosed(reason)));
    }

    public Long id() {
        return id;
    }

    public String owner() {
        return owner;
    }

    public AccountNumber accountNumber() {
        return accountNumber;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public Status status() {
        ensureReplayed();
        return status;
    }

    public Money balance() {
        ensureReplayed();
        return balance;
    }

    /** The version of the last event. */
    public long version() {
        return events.size();
    }

    /** The entries appended since this instance was loaded or opened. */
    public List<WalletEventEntry> newEntries() {
        ensureReplayed();
        return List.copyOf(events.subList(replayedEntries, events.size()));
    }

    private void ensureReplayed() {
        if (replayed) {
            return;
        }
        balance = Money.ZERO;
        debits = new HashMap<>();
        credits = new HashMap<>();
        deposits = new HashMap<>();
        events.forEach(entry -> apply(entry.payload()));
        replayedEntries = events.size();
        replayed = true;
    }

    private <E extends WalletEvent> E raise(E event) {
        apply(event);
        events.add(new WalletEventEntry(this, events.size() + 1, event));
        return event;
    }

    private void apply(WalletEvent event) {
        switch (event) {
            case WalletOpened _ -> status = Status.ACTIVE;
            case FundsDeposited e -> {
                balance = balance.plus(e.amount());
                deposits.put(e.paymentReference(), e.amount());
            }
            case FundsDebited e -> {
                balance = balance.minus(e.amount());
                debits.put(e.transferId(), new Debit(e.amount(), false));
            }
            case FundsCredited e -> {
                balance = balance.plus(e.amount());
                credits.put(e.transferId(), e.amount());
            }
            case DebitReversed e -> {
                balance = balance.plus(e.amount());
                debits.computeIfPresent(e.transferId(), (id, debit) -> debit.reverse());
            }
            case WalletFrozen _ -> status = Status.FROZEN;
            case WalletUnfrozen _ -> status = Status.ACTIVE;
            case WalletClosed _ -> status = Status.CLOSED;
        }
    }

    private static boolean alreadyApplied(Money stored, Money received, String operationId) {
        if (stored == null) {
            return false;
        }
        if (!stored.equals(received)) {
            throw new OperationConflictException(operationId, stored, received);
        }
        return true;
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException(amount);
        }
    }

    private void requireNotClosed() {
        if (status == Status.CLOSED) {
            throw new WalletClosedException(owner);
        }
    }

    private record Debit(Money amount, boolean reversed) {
        Debit reverse() {
            return new Debit(amount, true);
        }
    }
}
