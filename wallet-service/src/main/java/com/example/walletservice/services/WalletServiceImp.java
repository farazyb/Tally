package com.example.walletservice.services;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.TopUpId;
import com.example.walletservice.domain.TransferId;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.domain.WalletEventEntry;
import com.example.walletservice.domain.exception.WalletRuleViolation;
import com.example.walletservice.integration.events.WalletEventPublisher;
import com.example.walletservice.integration.replies.CommandReplies;
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
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The wallet's use cases. Each runs in its own transaction per attempt; the domain events it raises and its reply
 * are written to the outbox in that transaction. Must not be called inside an outer transaction, which the attempts
 * would join.
 */
@Service
@AllArgsConstructor
@Slf4j
public class WalletServiceImp implements WalletService {
    static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 3;
    static final int MAX_VERSION_ATTEMPTS = 3;
    static final String DEBIT_WALLET = "DebitWallet";
    static final String CREDIT_WALLET = "CreditWallet";
    static final String REVERSE_DEBIT = "ReverseDebit";
    static final String DEPOSIT_FUNDS = "DepositFunds";

    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");

    private final WalletRepository walletRepository;
    private final WalletEventPublisher eventPublisher;
    private final CommandReplies replies;
    private final TransactionTemplate transactionTemplate;
    private final RandomGenerator random = new SecureRandom();

    /**
     * Creates the client's wallet. Idempotent: events are delivered at least once, so a client
     * that already has a wallet is skipped. The unique constraints on owner and creation request id
     * catch a concurrent duplicate.
     * <p>
     * The account number is random, so it can collide with an existing one. Postgres aborts a transaction
     * after a failed insert, so each attempt runs in its own transaction and a collision retries with a new
     * number.
     */
    @Override
    public void createWallet(String owner, String creationRequestId) {
        inTransactionWithRetry(Wallet.ACCOUNT_NUMBER_CONSTRAINT, MAX_ACCOUNT_NUMBER_ATTEMPTS, owner,
                () -> createWalletOnce(owner, creationRequestId));
    }

    @Override
    public void debit(String owner, Money amount, TransferId transferId, Counterparty counterparty) {
        inTransactionWithRetry(WalletEventEntry.VERSION_CONSTRAINT, MAX_VERSION_ATTEMPTS, owner, () -> {
            Optional<Wallet> found = walletRepository.findByOwnerWithEvents(owner);
            if (found.isEmpty()) {
                replies.walletNotFound(DEBIT_WALLET, owner, transferId.value());
                return;
            }
            Wallet wallet = found.get();
            try {
                wallet.debit(amount, transferId, counterparty);
            } catch (WalletRuleViolation violation) {
                replies.rejected(DEBIT_WALLET, owner, transferId.value(), violation);
                return;
            }
            walletRepository.flush();
            eventPublisher.publishNew(wallet);
            replies.succeeded(DEBIT_WALLET, wallet, transferId.value());
        });
    }

    @Override
    public void credit(String owner, Money amount, TransferId transferId, Counterparty counterparty) {
        inTransactionWithRetry(WalletEventEntry.VERSION_CONSTRAINT, MAX_VERSION_ATTEMPTS, owner, () -> {
            Optional<Wallet> found = walletRepository.findByOwnerWithEvents(owner);
            if (found.isEmpty()) {
                replies.walletNotFound(CREDIT_WALLET, owner, transferId.value());
                return;
            }
            Wallet wallet = found.get();
            try {
                wallet.credit(amount, transferId, counterparty);
            } catch (WalletRuleViolation violation) {
                replies.rejected(CREDIT_WALLET, owner, transferId.value(), violation);
                return;
            }
            walletRepository.flush();
            eventPublisher.publishNew(wallet);
            replies.succeeded(CREDIT_WALLET, wallet, transferId.value());
        });
    }

    @Override
    public void reverseDebit(String owner, TransferId transferId, String reason) {
        inTransactionWithRetry(WalletEventEntry.VERSION_CONSTRAINT, MAX_VERSION_ATTEMPTS, owner, () -> {
            Optional<Wallet> found = walletRepository.findByOwnerWithEvents(owner);
            if (found.isEmpty()) {
                replies.walletNotFound(REVERSE_DEBIT, owner, transferId.value());
                return;
            }
            Wallet wallet = found.get();
            try {
                wallet.reverseDebit(transferId, reason);
            } catch (WalletRuleViolation violation) {
                replies.rejected(REVERSE_DEBIT, owner, transferId.value(), violation);
                return;
            }
            walletRepository.flush();
            eventPublisher.publishNew(wallet);
            replies.succeeded(REVERSE_DEBIT, wallet, transferId.value());
        });
    }

    @Override
    public void deposit(String owner, Money amount, TopUpId topUpId, PaymentReference paymentReference) {
        inTransactionWithRetry(WalletEventEntry.VERSION_CONSTRAINT, MAX_VERSION_ATTEMPTS, owner, () -> {
            Optional<Wallet> found = walletRepository.findByOwnerWithEvents(owner);
            if (found.isEmpty()) {
                replies.walletNotFound(DEPOSIT_FUNDS, owner, topUpId.value());
                return;
            }
            Wallet wallet = found.get();
            try {
                wallet.deposit(amount, topUpId, paymentReference);
            } catch (WalletRuleViolation violation) {
                replies.rejected(DEPOSIT_FUNDS, owner, topUpId.value(), violation);
                return;
            }
            walletRepository.flush();
            eventPublisher.publishNew(wallet);
            replies.succeeded(DEPOSIT_FUNDS, wallet, topUpId.value());
        });
    }

    @Override
    public void close(String owner, String reason) {
        inTransactionWithRetry(WalletEventEntry.VERSION_CONSTRAINT, MAX_VERSION_ATTEMPTS, owner, () -> {
            Wallet wallet = walletRepository.findByOwnerWithEvents(owner)
                    .orElseThrow(() -> new WalletNotYetOpenedException(owner));
            wallet.close(reason);
            walletRepository.flush();
            eventPublisher.publishNew(wallet);
        });
    }

    private void createWalletOnce(String owner, String creationRequestId) {
        if (walletRepository.existsByOwner(owner)) {
            log.info("Wallet for owner {} already exists, skipping event {}", owner, creationRequestId);
            return;
        }
        // Flush so the INSERT runs: it fills createdAt and surfaces a collision inside this attempt.
        Wallet wallet = walletRepository.saveAndFlush(
                Wallet.open(owner, creationRequestId, AccountNumber.generate(random)));

        // Same transaction as the insert: the event exists if and only if the wallet was committed.
        eventPublisher.publishNew(wallet);
        log.info("Created wallet for owner {} (event {})", owner, creationRequestId);
        SECURITY.atInfo()
                .addKeyValue("securityEvent", "wallet_created")
                .addKeyValue("owner", owner)
                .addKeyValue("walletId", wallet.id())
                .addKeyValue("eventId", creationRequestId)
                .log("Wallet created");
    }

    private void inTransactionWithRetry(String constraint, int maxAttempts, String owner, Runnable attempt) {
        for (int attemptNumber = 1; ; attemptNumber++) {
            try {
                transactionTemplate.executeWithoutResult(status -> attempt.run());
                return;
            } catch (DataIntegrityViolationException e) {
                if (!violates(e, constraint) || attemptNumber == maxAttempts) {
                    throw e;
                }
                log.warn("{} violated for owner {}, retrying (attempt {} of {})",
                        constraint, owner, attemptNumber, maxAttempts);
            }
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraint.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
