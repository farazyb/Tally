package com.example.walletservice.domain;

import com.example.walletservice.domain.events.WalletEvent;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One event in a wallet's stream. */
@Entity
@Immutable
@Table(name = "wallet_event",
        uniqueConstraints = {
                @UniqueConstraint(name = WalletEventEntry.VERSION_CONSTRAINT, columnNames = {"wallet_id", "version"}),
                @UniqueConstraint(name = "uk_wallet_event_event_id", columnNames = "event_id")
        })
public class WalletEventEntry {
    public static final String VERSION_CONSTRAINT = "uk_wallet_event_wallet_version";

    @Id
    @SequenceGenerator(
            name = "wallet_event_id_seq",
            sequenceName = "wallet_event_sequence",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "wallet_event_id_seq")
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Column(name = "type", nullable = false, updatable = false, length = 64)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private WalletEvent payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected WalletEventEntry() {
        // for JPA
    }

    WalletEventEntry(Wallet wallet, long version, WalletEvent payload) {
        this.eventId = UUID.randomUUID();
        this.wallet = Objects.requireNonNull(wallet);
        this.version = version;
        this.type = payload.getClass().getSimpleName();
        this.payload = payload;
        this.occurredAt = Instant.now();
    }

    public UUID eventId() {
        return eventId;
    }

    public long version() {
        return version;
    }

    public String type() {
        return type;
    }

    public WalletEvent payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
