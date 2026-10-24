package com.example.walletservice.services;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.integration.WalletCreatedEvent;
import com.example.common.outbox.OutboxWriter;
import com.example.walletservice.repository.WalletRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Service
@AllArgsConstructor
@Slf4j
public class WalletServiceImp implements WalletService {
    static final String AGGREGATE_TYPE = "wallet";
    static final String WALLET_CREATED = "WalletCreated";
    static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 3;

    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");

    private final WalletRepository walletRepository;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate transactionTemplate;
    private final RandomGenerator random = new SecureRandom();

    /**
     * Creates the client's wallet. Idempotent: events are delivered at least once, so a client
     * that already has a wallet is skipped. The unique constraints on owner and creation request id
     * catch a concurrent duplicate.
     * <p>
     * The account number is random, so it can collide with an existing one. Postgres aborts a transaction
     * after a failed insert, so each attempt runs in its own transaction and a collision retries with a new
     * number. Must not be called inside an outer transaction, which the attempts would join.
     */
    @Override
    public void createWallet(String owner, String creationRequestId) {
        for (int attempt = 1; ; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> createWalletOnce(owner, creationRequestId));
                return;
            } catch (DataIntegrityViolationException e) {
                if (!isAccountNumberCollision(e) || attempt == MAX_ACCOUNT_NUMBER_ATTEMPTS) {
                    throw e;
                }
                log.warn("Account number collision for owner {}, retrying (attempt {} of {})",
                        owner, attempt, MAX_ACCOUNT_NUMBER_ATTEMPTS);
            }
        }
    }

    private void createWalletOnce(String owner, String creationRequestId) {
        if (walletRepository.existsByOwner(owner)) {
            log.info("Wallet for owner {} already exists, skipping event {}", owner, creationRequestId);
            return;
        }
        // Flush so the INSERT runs: it fills createdAt for the event and surfaces a collision inside this attempt.
        Wallet wallet = walletRepository.saveAndFlush(
                Wallet.open(owner, creationRequestId, AccountNumber.generate(random)));

        // Same transaction as the insert: the event exists if and only if the wallet was committed.
        outboxWriter.write(AGGREGATE_TYPE, owner, WALLET_CREATED, new WalletCreatedEvent(owner, wallet.createdAt()));
        log.info("Created wallet for owner {} (event {})", owner, creationRequestId);
        SECURITY.atInfo()
                .addKeyValue("securityEvent", "wallet_created")
                .addKeyValue("owner", owner)
                .addKeyValue("walletId", wallet.id())
                .addKeyValue("eventId", creationRequestId)
                .log("Wallet created");
    }

    private static boolean isAccountNumberCollision(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return Wallet.ACCOUNT_NUMBER_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
