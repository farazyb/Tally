package com.example.walletservice;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.TopUpId;
import com.example.walletservice.domain.TransferId;
import com.example.walletservice.services.WalletNotYetOpenedException;
import com.example.walletservice.services.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

// Listeners stay stopped so the test never consumes from a locally running Kafka.
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class WalletCommandsIntegrationTest {
    private static final Counterparty COUNTERPARTY =
            new Counterparty(AccountNumber.generate(new Random(1)), "Jane Doe");

    @Autowired
    private WalletService walletService;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void anAppliedCommandPublishesItsEventAndRepliesSucceeded() {
        String owner = openWallet("owner-applied");
        TopUpId topUp = new TopUpId(UUID.randomUUID());

        walletService.deposit(owner, Money.parse("100.00"), topUp, new PaymentReference("payment-applied"));

        assertThat(outboxRows(owner)).extracting(row -> tuple(row.get("aggregate_type"), row.get("event_type")))
                .containsExactlyInAnyOrder(
                        tuple("events", "WalletOpened"),
                        tuple("events", "FundsDeposited"),
                        tuple("replies", "DepositFundsSucceeded"));
        Map<String, Object> reply = row(owner, "DepositFundsSucceeded");
        assertThat(reply.get("operation_id")).isEqualTo(topUp.value().toString());
        assertThat(reply.get("balance_after")).isEqualTo("10000");
        assertThat(reply.get("wallet_version")).isEqualTo("2");
        Map<String, Object> event = row(owner, "FundsDeposited");
        assertThat(event.get("event_id")).isEqualTo(storedEventId(owner, 2));
        assertThat(event.get("balance_after")).isEqualTo("10000");
    }

    @Test
    void aDuplicateCommandRepliesSucceededAgainWithoutASecondEvent() {
        String owner = openWallet("owner-duplicate");
        walletService.deposit(owner, Money.parse("100.00"), new TopUpId(UUID.randomUUID()),
                new PaymentReference("payment-duplicate"));
        TransferId transfer = new TransferId(UUID.randomUUID());

        walletService.debit(owner, Money.parse("30.00"), transfer, COUNTERPARTY);
        walletService.debit(owner, Money.parse("30.00"), transfer, COUNTERPARTY);

        assertThat(count(owner, "FundsDebited")).isEqualTo(1);
        assertThat(count(owner, "DebitWalletSucceeded")).isEqualTo(2);
    }

    @Test
    void aBrokenRuleRepliesRejectedWithoutAnEvent() {
        String owner = openWallet("owner-rejected");

        walletService.debit(owner, Money.parse("30.00"), new TransferId(UUID.randomUUID()), COUNTERPARTY);

        assertThat(count(owner, "FundsDebited")).isZero();
        assertThat(row(owner, "DebitWalletRejected").get("reason")).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void aZeroAmountRepliesInvalidAmount() {
        String owner = openWallet("owner-zero");

        walletService.deposit(owner, new Money(0), new TopUpId(UUID.randomUUID()), new PaymentReference("payment-zero"));

        assertThat(count(owner, "FundsDeposited")).isZero();
        assertThat(row(owner, "DepositFundsRejected").get("reason")).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void aClosedWalletRepliesWalletClosed() {
        String owner = openWallet("owner-closed");
        walletService.close(owner, "profile deleted");

        walletService.credit(owner, Money.parse("5.00"), new TransferId(UUID.randomUUID()), COUNTERPARTY);

        assertThat(count(owner, "FundsCredited")).isZero();
        assertThat(row(owner, "CreditWalletRejected").get("reason")).isEqualTo("WALLET_CLOSED");
    }

    @Test
    void aCommandForAnUnknownOwnerRepliesWalletNotFound() {
        walletService.credit("owner-unknown", Money.parse("5.00"), new TransferId(UUID.randomUUID()), COUNTERPARTY);

        assertThat(row("owner-unknown", "CreditWalletRejected").get("reason")).isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    void aTransferLegAndItsReversalArePublished() {
        String owner = openWallet("owner-reversal");
        walletService.deposit(owner, Money.parse("100.00"), new TopUpId(UUID.randomUUID()),
                new PaymentReference("payment-reversal"));
        TransferId transfer = new TransferId(UUID.randomUUID());

        walletService.debit(owner, Money.parse("30.00"), transfer, COUNTERPARTY);
        walletService.reverseDebit(owner, transfer, "credit rejected");
        walletService.credit(owner, Money.parse("5.00"), new TransferId(UUID.randomUUID()), COUNTERPARTY);

        assertThat(count(owner, "DebitReversed")).isEqualTo(1);
        assertThat(count(owner, "ReverseDebitSucceeded")).isEqualTo(1);
        assertThat(row(owner, "FundsCredited").get("balance_after")).isEqualTo("10500");
    }

    @Test
    void closingPublishesWalletClosedAndSendsNoReply() {
        String owner = openWallet("owner-close");

        walletService.close(owner, "profile deleted");

        assertThat(count(owner, "WalletClosed")).isEqualTo(1);
        assertThat(outboxRows(owner)).extracting(row -> row.get("aggregate_type")).doesNotContain("replies");
    }

    @Test
    void closingAWalletThatDoesNotExistYetThrowsTheRetryableException() {
        assertThatExceptionOfType(WalletNotYetOpenedException.class)
                .isThrownBy(() -> walletService.close("owner-not-yet", "profile deleted"));
    }

    private String openWallet(String owner) {
        walletService.createWallet(owner, "request-" + owner);
        return owner;
    }

    private List<Map<String, Object>> outboxRows(String owner) {
        return jdbcClient.sql("select aggregate_type, event_type from outbox_event where aggregate_id = :owner")
                .param("owner", owner).query().listOfRows();
    }

    private long count(String owner, String eventType) {
        return jdbcClient.sql("select count(*) from outbox_event where aggregate_id = :owner and event_type = :type")
                .param("owner", owner).param("type", eventType).query(Long.class).single();
    }

    private Map<String, Object> row(String owner, String eventType) {
        return jdbcClient.sql("""
                        select payload ->> 'eventId'           as event_id,
                               payload ->> 'operationId'       as operation_id,
                               payload ->> 'balanceAfterMinor' as balance_after,
                               payload ->> 'walletVersion'     as wallet_version,
                               payload ->> 'reason'            as reason
                        from outbox_event where aggregate_id = :owner and event_type = :type
                        """)
                .param("owner", owner).param("type", eventType).query().singleRow();
    }

    private String storedEventId(String owner, long version) {
        return jdbcClient.sql("""
                        select e.event_id::text from wallet_event e join wallet w on w.id = e.wallet_id
                        where w.owner = :owner and e.version = :version
                        """)
                .param("owner", owner).param("version", version).query(String.class).single();
    }
}
