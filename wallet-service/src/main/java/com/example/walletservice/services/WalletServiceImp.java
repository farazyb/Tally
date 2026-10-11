package com.example.walletservice.services;

import com.example.walletservice.domain.Status;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.repository.WalletRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Slf4j
public class WalletServiceImp implements WalletService {
    private final WalletRepository walletRepository;

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
        walletRepository.save(Wallet.builder()
                .subKey(subKey)
                .idempotencyKey(idempotencyKey)
                .status(Status.ACTIVE)
                .build());
        log.info("Created wallet for subKey {} (event {})", subKey, idempotencyKey);
    }
}
