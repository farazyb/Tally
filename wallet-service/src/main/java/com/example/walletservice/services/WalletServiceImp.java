package com.example.walletservice.services;

import com.example.walletservice.domain.Status;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.events.WalletCreatedEvent;
import com.example.walletservice.outbox.OutboxWriter;
import com.example.walletservice.repository.WalletRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Slf4j
public class WalletServiceImp implements WalletService {
    static final String AGGREGATE_TYPE = "wallet";
    static final String WALLET_CREATED = "WalletCreated";

    private final WalletRepository walletRepository;
    private final OutboxWriter outboxWriter;

    /**
     * Creates the client's wallet. Idempotent: events are delivered at least once, so a client
     * that already has a wallet is skipped. The unique constraints on sub_key and idempotency_key
     * catch a concurrent duplicate.
     */
    @Override
    @Transactional
    public void createWallet(String subKey, String idempotencyKey) {
        if (walletRepository.existsBySubKey(subKey)) {
            log.info("Wallet for subKey {} already exists, skipping event {}", subKey, idempotencyKey);
            return;
        }
        // Flush so the INSERT runs and @CreationTimestamp fills createdAt for the event.
        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .subKey(subKey)
                .idempotencyKey(idempotencyKey)
                .status(Status.ACTIVE)
                .build());

        // Same transaction as the insert: the event exists if and only if the wallet was committed.
        outboxWriter.write(AGGREGATE_TYPE, subKey, WALLET_CREATED, new WalletCreatedEvent(subKey, wallet.getCreatedAt()));
        log.info("Created wallet for subKey {} (event {})", subKey, idempotencyKey);
    }
}
