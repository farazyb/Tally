package com.example.walletservice;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.repository.WalletRepository;
import com.example.walletservice.services.WalletService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// Listeners stay stopped so the test never consumes from a locally running Kafka.
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class WalletCreationIntegrationTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createsAWalletWithAValidAccountNumberAndAnOutboxEvent() {
        walletService.createWallet("owner-created", "event-created");

        Map<String, Object> row = jdbcClient.sql("""
                        select owner, creation_request_id, account_number from wallet where owner = :owner
                        """)
                .param("owner", "owner-created")
                .query().singleRow();
        assertThat(row.get("creation_request_id")).isEqualTo("event-created");
        assertThat(new AccountNumber((String) row.get("account_number")).value()).startsWith(AccountNumber.PREFIX);
        assertThat(outboxEventCount("owner-created")).isEqualTo(1);
    }

    @Test
    void aSecondDeliveryForTheSameOwnerIsSkipped() {
        walletService.createWallet("owner-twice", "event-twice-1");
        walletService.createWallet("owner-twice", "event-twice-2");

        assertThat(jdbcClient.sql("select count(*) from wallet where owner = :owner")
                .param("owner", "owner-twice").query(Long.class).single()).isEqualTo(1);
        assertThat(outboxEventCount("owner-twice")).isEqualTo(1);
    }

    @Test
    void aDuplicateAccountNumberIsReportedWithTheAccountNumberConstraint() {
        walletService.createWallet("owner-first", "event-first");
        AccountNumber taken = new AccountNumber(jdbcClient.sql("select account_number from wallet where owner = :owner")
                .param("owner", "owner-first").query(String.class).single());

        DataIntegrityViolationException e = insertFailure("owner-second", "event-second", taken);

        assertThat(constraintName(e)).isEqualTo(Wallet.ACCOUNT_NUMBER_CONSTRAINT);
    }

    @Test
    void aDuplicateOwnerIsReportedWithTheRenamedOwnerConstraint() {
        walletService.createWallet("owner-dup", "event-dup");

        DataIntegrityViolationException e = insertFailure("owner-dup", "event-dup-2",
                AccountNumber.generate(new Random()));

        assertThat(constraintName(e)).isEqualTo("wallet_owner_key");
    }

    private DataIntegrityViolationException insertFailure(String owner, String creationRequestId, AccountNumber accountNumber) {
        return catchThrowableOfType(DataIntegrityViolationException.class,
                () -> transactionTemplate.executeWithoutResult(status -> walletRepository.saveAndFlush(
                        Wallet.open(owner, creationRequestId, accountNumber))));
    }

    private long outboxEventCount(String owner) {
        return jdbcClient.sql("select count(*) from outbox_event where aggregate_id = :owner and event_type = 'WalletCreated'")
                .param("owner", owner).query(Long.class).single();
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
