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
import com.example.walletservice.domain.exception.RejectionReason;
import com.example.walletservice.domain.exception.WalletClosedException;
import com.example.walletservice.domain.exception.WalletFrozenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class WalletTest {
    private static final String OWNER = "owner-1";
    private static final String CREATION_REQUEST = "request-1";
    private static final AccountNumber ACCOUNT = new AccountNumber("6219861234567898");
    private static final Counterparty COUNTERPARTY =
            new Counterparty(AccountNumber.generate(new Random(1)), "Jane Doe");

    private static final TransferId T1 = transferId("t1");
    private static final TransferId T9 = transferId("t9");
    private static final TopUpId U1 = new TopUpId(UUID.nameUUIDFromBytes("u1".getBytes(StandardCharsets.UTF_8)));
    private static final PaymentReference P1 = new PaymentReference("p1");

    @Test
    void openingAWalletMakesItActiveWithAZeroBalance() {
        Wallet wallet = Wallet.open(OWNER, CREATION_REQUEST, ACCOUNT);

        assertThat(wallet.status()).isEqualTo(Status.ACTIVE);
        assertThat(wallet.balance()).isEqualTo(Money.ZERO);
        assertThat(wallet.version()).isEqualTo(1);
        assertThat(wallet.owner()).isEqualTo(OWNER);
        assertThat(wallet.accountNumber()).isEqualTo(ACCOUNT);
    }

    @Test
    void debitWithinTheBalanceRecordsFundsDebited() {
        Wallet wallet = walletWith(deposited("100.00", P1));

        assertThat(wallet.debit(money("30.00"), T1, COUNTERPARTY))
                .contains(new FundsDebited(money("30.00"), T1, COUNTERPARTY));
        assertThat(wallet.balance()).isEqualTo(money("70.00"));
        assertThat(wallet.version()).isEqualTo(3);
    }

    @Test
    void debitAboveTheBalanceIsRejectedAndRecordsNothing() {
        Wallet wallet = walletWith(deposited("20.00", P1));

        assertThatExceptionOfType(InsufficientFundsException.class)
                .isThrownBy(() -> wallet.debit(money("30.00"), T1, COUNTERPARTY))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));
        assertThat(wallet.balance()).isEqualTo(money("20.00"));
        assertThat(wallet.version()).isEqualTo(2);
    }

    @Test
    void aFrozenWalletCannotSend() {
        Wallet wallet = walletWith(deposited("100.00", P1), new WalletFrozen("review"));

        assertThatExceptionOfType(WalletFrozenException.class)
                .isThrownBy(() -> wallet.debit(money("30.00"), T1, COUNTERPARTY))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RejectionReason.WALLET_FROZEN));
    }

    @Test
    void aFrozenWalletWithoutFundsReportsFrozenNotInsufficientFunds() {
        Wallet wallet = walletWith(new WalletFrozen("review"));

        assertThatExceptionOfType(WalletFrozenException.class)
                .isThrownBy(() -> wallet.debit(money("30.00"), T1, COUNTERPARTY));
    }

    @Test
    void aClosedWalletAcceptsNoMoney() {
        Wallet wallet = walletWith(deposited("100.00", P1), new WalletClosed("profile deleted"));

        assertThatExceptionOfType(WalletClosedException.class)
                .isThrownBy(() -> wallet.debit(money("30.00"), T1, COUNTERPARTY))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RejectionReason.WALLET_CLOSED));
        assertThatExceptionOfType(WalletClosedException.class)
                .isThrownBy(() -> wallet.credit(money("30.00"), T1, COUNTERPARTY));
        assertThatExceptionOfType(WalletClosedException.class)
                .isThrownBy(() -> wallet.deposit(money("30.00"), U1, new PaymentReference("p2")));
        assertThat(wallet.version()).isEqualTo(3);
    }

    @Test
    void aFrozenWalletCanStillReceive() {
        Wallet wallet = walletWith(new WalletFrozen("review"));

        assertThat(wallet.credit(money("30.00"), T1, COUNTERPARTY)).isPresent();
        assertThat(wallet.deposit(money("20.00"), U1, P1)).isPresent();
        assertThat(wallet.balance()).isEqualTo(money("50.00"));
    }

    @Test
    void aDuplicateDebitIsIgnoredEvenAfterTheWalletWasFrozen() {
        Wallet wallet = walletWith(deposited("100.00", P1), debited("30.00", T1), new WalletFrozen("review"));

        assertThat(wallet.debit(money("30.00"), T1, COUNTERPARTY)).isEmpty();
        assertThat(wallet.balance()).isEqualTo(money("70.00"));
        assertThat(wallet.version()).isEqualTo(4);
    }

    @Test
    void aDuplicateDebitAfterItsReversalDoesNotDebitAgain() {
        Wallet wallet = walletWith(deposited("100.00", P1), debited("30.00", T1),
                new DebitReversed(money("30.00"), T1, "credit rejected"));

        assertThat(wallet.debit(money("30.00"), T1, COUNTERPARTY)).isEmpty();
        assertThat(wallet.balance()).isEqualTo(money("100.00"));
    }

    @Test
    void reversingAnUnknownDebitIsRejected() {
        Wallet wallet = walletWith();

        assertThatExceptionOfType(DebitNotFoundException.class)
                .isThrownBy(() -> wallet.reverseDebit(T9, "credit rejected"))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RejectionReason.DEBIT_NOT_FOUND));
    }

    @Test
    void aReversalIsAcceptedOnAClosedWalletAndTakesTheAmountFromTheDebit() {
        Wallet wallet = walletWith(deposited("100.00", P1), debited("30.00", T1), new WalletClosed("profile deleted"));

        assertThat(wallet.reverseDebit(T1, "credit rejected"))
                .contains(new DebitReversed(money("30.00"), T1, "credit rejected"));
        assertThat(wallet.balance()).isEqualTo(money("100.00"));
    }

    @Test
    void aDuplicateReversalIsIgnored() {
        Wallet wallet = walletWith(deposited("100.00", P1), debited("30.00", T1),
                new DebitReversed(money("30.00"), T1, "credit rejected"));

        assertThat(wallet.reverseDebit(T1, "credit rejected")).isEmpty();
        assertThat(wallet.balance()).isEqualTo(money("100.00"));
    }

    @Test
    void aDuplicateCreditIsIgnored() {
        Wallet wallet = walletWith(new FundsCredited(money("30.00"), T1, COUNTERPARTY));

        assertThat(wallet.credit(money("30.00"), T1, COUNTERPARTY)).isEmpty();
        assertThat(wallet.balance()).isEqualTo(money("30.00"));
    }

    @Test
    void aDuplicateDepositIsIgnored() {
        Wallet wallet = walletWith(deposited("50.00", P1));

        assertThat(wallet.deposit(money("50.00"), U1, P1)).isEmpty();
        assertThat(wallet.balance()).isEqualTo(money("50.00"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void aMoneyCommandWithoutAPositiveAmountIsRejected(long amountMinor) {
        Wallet wallet = walletWith(deposited("100.00", P1));
        Money amount = new Money(amountMinor);

        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> wallet.debit(amount, T1, COUNTERPARTY))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RejectionReason.INVALID_AMOUNT));
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> wallet.credit(amount, T1, COUNTERPARTY));
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> wallet.deposit(amount, U1, new PaymentReference("p2")));
        assertThat(wallet.version()).isEqualTo(2);
    }

    @Test
    void freezeAndUnfreezeAreNoOpsInTheStateTheyLeadTo() {
        Wallet wallet = walletWith(new WalletFrozen("review"));

        assertThat(wallet.freeze("again")).isEmpty();
        assertThat(wallet.unfreeze("cleared")).contains(new WalletUnfrozen("cleared"));
        assertThat(wallet.unfreeze("again")).isEmpty();
        assertThat(wallet.status()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void aClosedWalletCannotBeFrozenOrUnfrozenAndClosingAgainIsANoOp() {
        Wallet wallet = walletWith(new WalletClosed("profile deleted"));

        assertThatExceptionOfType(WalletClosedException.class).isThrownBy(() -> wallet.freeze("review"));
        assertThatExceptionOfType(WalletClosedException.class).isThrownBy(() -> wallet.unfreeze("cleared"));
        assertThat(wallet.close("again")).isEmpty();
    }

    @Test
    void closingKeepsTheBalance() {
        Wallet wallet = walletWith(deposited("50.00", P1));

        assertThat(wallet.close("profile deleted")).contains(new WalletClosed("profile deleted"));
        assertThat(wallet.status()).isEqualTo(Status.CLOSED);
        assertThat(wallet.balance()).isEqualTo(money("50.00"));
    }

    @Test
    void aFrozenWalletCanBeClosed() {
        Wallet wallet = walletWith(new WalletFrozen("review"));

        assertThat(wallet.close("profile deleted")).isPresent();
        assertThat(wallet.status()).isEqualTo(Status.CLOSED);
    }

    @Test
    void aFreshInstanceReplayedFromTheSameEventsHasTheSameState() {
        Wallet wallet = Wallet.open(OWNER, CREATION_REQUEST, ACCOUNT);
        List<WalletEvent> history = new ArrayList<>();
        history.add(new WalletOpened(OWNER, ACCOUNT));
        wallet.deposit(money("100.00"), U1, P1).ifPresent(history::add);
        wallet.debit(money("30.00"), T1, COUNTERPARTY).ifPresent(history::add);
        wallet.reverseDebit(T1, "credit rejected").ifPresent(history::add);
        wallet.credit(money("5.00"), T9, COUNTERPARTY).ifPresent(history::add);
        wallet.freeze("review").ifPresent(history::add);

        Wallet replayed = Wallet.fromHistory(OWNER, CREATION_REQUEST, ACCOUNT, history);

        assertThat(replayed.status()).isEqualTo(wallet.status()).isEqualTo(Status.FROZEN);
        assertThat(replayed.balance()).isEqualTo(wallet.balance()).isEqualTo(money("105.00"));
        assertThat(replayed.version()).isEqualTo(wallet.version()).isEqualTo(6);
        assertThat(replayed.debit(money("30.00"), T1, COUNTERPARTY)).isEmpty();
        assertThat(replayed.reverseDebit(T1, "credit rejected")).isEmpty();
    }

    @Test
    void aDebitWithAKnownTransferIdButAnotherAmountIsAConflict() {
        Wallet wallet = walletWith(deposited("100.00", P1), debited("30.00", T1));

        assertThatExceptionOfType(OperationConflictException.class)
                .isThrownBy(() -> wallet.debit(money("40.00"), T1, COUNTERPARTY))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RejectionReason.OPERATION_CONFLICT));
        assertThat(wallet.version()).isEqualTo(3);
    }

    @Test
    void aCreditWithAKnownTransferIdButAnotherAmountIsAConflict() {
        Wallet wallet = walletWith(new FundsCredited(money("30.00"), T1, COUNTERPARTY));

        assertThatExceptionOfType(OperationConflictException.class)
                .isThrownBy(() -> wallet.credit(money("40.00"), T1, COUNTERPARTY));
    }

    @Test
    void aDepositWithAKnownPaymentReferenceButAnotherAmountIsAConflict() {
        Wallet wallet = walletWith(deposited("50.00", P1));

        assertThatExceptionOfType(OperationConflictException.class)
                .isThrownBy(() -> wallet.deposit(money("60.00"), U1, P1));
    }

    private static Wallet walletWith(WalletEvent... events) {
        List<WalletEvent> history = new ArrayList<>();
        history.add(new WalletOpened(OWNER, ACCOUNT));
        history.addAll(List.of(events));
        return Wallet.fromHistory(OWNER, CREATION_REQUEST, ACCOUNT, history);
    }

    private static FundsDeposited deposited(String amount, PaymentReference reference) {
        return new FundsDeposited(money(amount), U1, reference);
    }

    private static FundsDebited debited(String amount, TransferId transferId) {
        return new FundsDebited(money(amount), transferId, COUNTERPARTY);
    }

    private static Money money(String amount) {
        return Money.parse(amount);
    }

    private static TransferId transferId(String name) {
        return new TransferId(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
    }
}
