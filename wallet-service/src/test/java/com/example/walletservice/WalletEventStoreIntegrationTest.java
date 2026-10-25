package com.example.walletservice;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.Status;
import com.example.walletservice.domain.TopUpId;
import com.example.walletservice.domain.TransferId;
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
import com.example.walletservice.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// Listeners stay stopped so the test never consumes from a locally running Kafka.
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class WalletEventStoreIntegrationTest {
    private static final Counterparty COUNTERPARTY =
            new Counterparty(AccountNumber.generate(new Random(1)), "Jane Doe");

    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void everyEventTypeIsStoredAndReadBackAndNewEventsAreInsertedOnCommit() {
        String owner = "owner-round-trip";
        AccountNumber account = AccountNumber.generate(new Random(2));
        TransferId debitTransfer = new TransferId(UUID.randomUUID());
        TransferId creditTransfer = new TransferId(UUID.randomUUID());
        TopUpId topUp = new TopUpId(UUID.randomUUID());
        PaymentReference payment = new PaymentReference("payment-1");

        transactionTemplate.executeWithoutResult(status ->
                walletRepository.saveAndFlush(Wallet.open(owner, "request-round-trip", account)));
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByOwnerWithEvents(owner).orElseThrow();
            wallet.deposit(Money.parse("100.00"), topUp, payment);
            wallet.debit(Money.parse("30.00"), debitTransfer, COUNTERPARTY);
            wallet.reverseDebit(debitTransfer, "credit rejected");
            wallet.credit(Money.parse("5.00"), creditTransfer, COUNTERPARTY);
            wallet.freeze("review");
            wallet.unfreeze("cleared");
            wallet.close("profile deleted");
        });

        List<WalletEventEntry> entries = entriesOf(owner);
        assertThat(entries).extracting(WalletEventEntry::version).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(entries).extracting(WalletEventEntry::type).containsExactly(
                "WalletOpened", "FundsDeposited", "FundsDebited", "DebitReversed",
                "FundsCredited", "WalletFrozen", "WalletUnfrozen", "WalletClosed");
        assertThat(entries).extracting(WalletEventEntry::payload).containsExactly(
                new WalletOpened(owner, account),
                new FundsDeposited(Money.parse("100.00"), topUp, payment),
                new FundsDebited(Money.parse("30.00"), debitTransfer, COUNTERPARTY),
                new DebitReversed(Money.parse("30.00"), debitTransfer, "credit rejected"),
                new FundsCredited(Money.parse("5.00"), creditTransfer, COUNTERPARTY),
                new WalletFrozen("review"),
                new WalletUnfrozen("cleared"),
                new WalletClosed("profile deleted"));
        assertThat(entries).extracting(WalletEventEntry::eventId).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(entries).extracting(WalletEventEntry::occurredAt).doesNotContainNull();

        Wallet reloaded = transactionTemplate.execute(status -> walletRepository.findByOwnerWithEvents(owner).orElseThrow());
        assertThat(reloaded.status()).isEqualTo(Status.CLOSED);
        assertThat(reloaded.balance()).isEqualTo(Money.parse("105.00"));
        assertThat(reloaded.version()).isEqualTo(8);
    }

    @Test
    void aSecondWriterOfTheSameVersionIsRejectedByTheVersionConstraint() {
        String owner = "owner-conflict";
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = Wallet.open(owner, "request-conflict", AccountNumber.generate(new Random(3)));
            wallet.deposit(Money.parse("100.00"), new TopUpId(UUID.randomUUID()), new PaymentReference("payment-2"));
            walletRepository.saveAndFlush(wallet);
        });
        TransactionTemplate otherWriter = new TransactionTemplate(transactionManager);
        otherWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        DataIntegrityViolationException conflict = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    Wallet first = walletRepository.findByOwnerWithEvents(owner).orElseThrow();
                    first.debit(Money.parse("40.00"), new TransferId(UUID.randomUUID()), COUNTERPARTY);
                    otherWriter.executeWithoutResult(inner -> walletRepository.findByOwnerWithEvents(owner).orElseThrow()
                            .debit(Money.parse("40.00"), new TransferId(UUID.randomUUID()), COUNTERPARTY));
                    walletRepository.flush();
                }));

        assertThat(constraintName(conflict)).isEqualTo(WalletEventEntry.VERSION_CONSTRAINT);
        Wallet stored = transactionTemplate.execute(status -> walletRepository.findByOwnerWithEvents(owner).orElseThrow());
        assertThat(stored.version()).isEqualTo(3);
        assertThat(stored.balance()).isEqualTo(Money.parse("60.00"));
    }

    private List<WalletEventEntry> entriesOf(String owner) {
        return transactionTemplate.execute(status -> entityManager.createQuery("""
                        select e from WalletEventEntry e join e.wallet w where w.owner = :owner order by e.version
                        """, WalletEventEntry.class)
                .setParameter("owner", owner)
                .getResultList());
    }

    private static String constraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
