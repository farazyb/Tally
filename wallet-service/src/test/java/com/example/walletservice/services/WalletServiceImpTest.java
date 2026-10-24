package com.example.walletservice.services;

import com.example.common.outbox.OutboxWriter;
import com.example.walletservice.domain.Wallet;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceImpTest {
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private WalletServiceImp walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImp(walletRepository, outboxWriter, new TransactionTemplate(transactionManager));
        when(walletRepository.existsByOwner("owner-1")).thenReturn(false);
    }

    @Test
    void createsTheWalletWithAValidAccountNumber() {
        when(walletRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        walletService.createWallet("owner-1", "event-1");

        Wallet saved = captureSavedWallets(1).getFirst();
        assertThat(saved.accountNumber()).isNotNull();
        verify(outboxWriter).write(eq(WalletServiceImp.AGGREGATE_TYPE), eq("owner-1"), eq(WalletServiceImp.WALLET_CREATED), any());
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
