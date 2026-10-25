package com.example.walletservice.services;

import com.example.common.outbox.OutboxWriter;
import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.TransferId;
import com.example.walletservice.domain.Wallet;
import com.example.walletservice.domain.WalletEventEntry;
import com.example.walletservice.integration.OutboxChannel;
import com.example.walletservice.integration.events.WalletEventPublisher;
import com.example.walletservice.integration.events.WalletOpenedV1;
import com.example.walletservice.integration.replies.CommandReplies;
import com.example.walletservice.repository.WalletRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceImpTest {
    private static final AccountNumber ACCOUNT = AccountNumber.generate(new Random(1));
    private static final TransferId TRANSFER = new TransferId(UUID.randomUUID());
    private static final Counterparty COUNTERPARTY = new Counterparty(AccountNumber.generate(new Random(2)), "Jane Doe");

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private WalletServiceImp walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImp(walletRepository, new WalletEventPublisher(outboxWriter),
                new CommandReplies(outboxWriter), new TransactionTemplate(transactionManager));
        when(walletRepository.existsByOwner("owner-1")).thenReturn(false);
    }

    @Test
    void createsTheWalletWithAValidAccountNumber() {
        when(walletRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        walletService.createWallet("owner-1", "event-1");

        Wallet saved = captureSavedWallets(1).getFirst();
        assertThat(saved.accountNumber()).isNotNull();
        verify(outboxWriter).write(eq(OutboxChannel.EVENTS), eq("owner-1"), eq("WalletOpened"), any(WalletOpenedV1.class));
        verify(transactionManager).commit(any());
    }

    @Test
    void retriesWithANewAccountNumberInANewTransactionAfterACollision() {
        when(walletRepository.saveAndFlush(any()))
                .thenThrow(violationOf(Wallet.ACCOUNT_NUMBER_CONSTRAINT))
                .thenAnswer(invocation -> invocation.getArgument(0));

        walletService.createWallet("owner-1", "event-1");

        List<Wallet> attempts = captureSavedWallets(2);
        assertThat(attempts.get(0).accountNumber()).isNotEqualTo(attempts.get(1).accountNumber());
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
        verify(outboxWriter, times(1)).write(anyString(), anyString(), anyString(), any());
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        when(walletRepository.saveAndFlush(any())).thenThrow(violationOf(Wallet.ACCOUNT_NUMBER_CONSTRAINT));

        assertThatThrownBy(() -> walletService.createWallet("owner-1", "event-1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        captureSavedWallets(WalletServiceImp.MAX_ACCOUNT_NUMBER_ATTEMPTS);
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNotRetryOtherConstraintViolations() {
        when(walletRepository.saveAndFlush(any())).thenThrow(violationOf("wallet_owner_key"));

        assertThatThrownBy(() -> walletService.createWallet("owner-1", "event-1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        captureSavedWallets(1);
    }

    @Test
    void skipsAClientThatAlreadyHasAWallet() {
        when(walletRepository.existsByOwner("owner-1")).thenReturn(true);

        walletService.createWallet("owner-1", "event-1");

        verify(walletRepository, never()).saveAndFlush(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retriesACommandInANewTransactionAfterAVersionConflict() {
        when(walletRepository.findByOwnerWithEvents("owner-1"))
                .thenAnswer(invocation -> Optional.of(Wallet.open("owner-1", "event-1", ACCOUNT)));
        doThrow(violationOf(WalletEventEntry.VERSION_CONSTRAINT)).doNothing().when(walletRepository).flush();

        walletService.credit("owner-1", new Money(500), TRANSFER, COUNTERPARTY);

        verify(walletRepository, times(2)).findByOwnerWithEvents("owner-1");
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
        verify(outboxWriter, times(1)).write(eq(OutboxChannel.REPLIES), eq("owner-1"), eq("CreditWalletSucceeded"), any());
        verify(outboxWriter, times(1)).write(eq(OutboxChannel.EVENTS), eq("owner-1"), eq("FundsCredited"), any());
    }

    @Test
    void givesUpOnAVersionConflictAfterTheMaximumNumberOfAttempts() {
        when(walletRepository.findByOwnerWithEvents("owner-1"))
                .thenAnswer(invocation -> Optional.of(Wallet.open("owner-1", "event-1", ACCOUNT)));
        doThrow(violationOf(WalletEventEntry.VERSION_CONSTRAINT)).when(walletRepository).flush();

        assertThatThrownBy(() -> walletService.credit("owner-1", new Money(500), TRANSFER, COUNTERPARTY))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(walletRepository, times(WalletServiceImp.MAX_VERSION_ATTEMPTS)).findByOwnerWithEvents("owner-1");
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNotRetryACommandOnAnotherConstraintViolation() {
        when(walletRepository.findByOwnerWithEvents("owner-1"))
                .thenAnswer(invocation -> Optional.of(Wallet.open("owner-1", "event-1", ACCOUNT)));
        doThrow(violationOf("uk_wallet_event_event_id")).when(walletRepository).flush();

        assertThatThrownBy(() -> walletService.credit("owner-1", new Money(500), TRANSFER, COUNTERPARTY))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(walletRepository, times(1)).findByOwnerWithEvents("owner-1");
    }

    private List<Wallet> captureSavedWallets(int expectedAttempts) {
        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository, times(expectedAttempts)).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"), constraintName));
    }
}
