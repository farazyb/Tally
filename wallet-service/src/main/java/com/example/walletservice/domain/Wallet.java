package com.example.walletservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "wallet")
public class Wallet {

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
    @Column(unique = true, nullable = false)
    private String subKey;

    // Id of the event that created this wallet (Debezium "id" header).
    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String accountNumber;
}
