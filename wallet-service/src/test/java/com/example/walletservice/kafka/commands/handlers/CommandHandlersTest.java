package com.example.walletservice.kafka.commands.handlers;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.TopUpId;
import com.example.walletservice.domain.TransferId;
import com.example.walletservice.services.WalletService;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CommandHandlersTest {
    private static final String ACCOUNT = "6219861234567898";
    private static final UUID TRANSFER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TOP_UP = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final WalletService walletService = mock(WalletService.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void debitWalletBuildsTheDomainValues() {
        new DebitWalletHandler(walletService, jsonMapper).handle("m1", """
                {"owner":"owner-1","transferId":"%s","amountMinor":3000,
                 "counterpartyAccountNumber":"%s","counterpartyName":"Jane Doe"}
                """.formatted(TRANSFER, ACCOUNT));

        verify(walletService).debit("owner-1", new Money(3000), new TransferId(TRANSFER),
                new Counterparty(new AccountNumber(ACCOUNT), "Jane Doe"));
    }

    @Test
    void creditWalletBuildsTheDomainValues() {
        new CreditWalletHandler(walletService, jsonMapper).handle("m1", """
                {"owner":"owner-1","transferId":"%s","amountMinor":3000,
                 "counterpartyAccountNumber":"%s","counterpartyName":"Jane Doe"}
                """.formatted(TRANSFER, ACCOUNT));

        verify(walletService).credit("owner-1", new Money(3000), new TransferId(TRANSFER),
                new Counterparty(new AccountNumber(ACCOUNT), "Jane Doe"));
    }

    @Test
    void reverseDebitBuildsTheDomainValues() {
        new ReverseDebitHandler(walletService, jsonMapper).handle("m1", """
                {"owner":"owner-1","transferId":"%s","reason":"credit rejected"}
                """.formatted(TRANSFER));

        verify(walletService).reverseDebit("owner-1", new TransferId(TRANSFER), "credit rejected");
    }

    @Test
    void depositFundsBuildsTheDomainValues() {
        new DepositFundsHandler(walletService, jsonMapper).handle("m1", """
                {"owner":"owner-1","topUpId":"%s","amountMinor":5000,"paymentReference":"p1"}
                """.formatted(TOP_UP));

        verify(walletService).deposit("owner-1", new Money(5000), new TopUpId(TOP_UP), new PaymentReference("p1"));
    }

    @Test
    void closeWalletPassesTheReason() {
        new CloseWalletHandler(walletService, jsonMapper).handle("m1", """
                {"owner":"owner-1","requestId":"%s","reason":"profile deleted"}
                """.formatted(UUID.randomUUID()));

        verify(walletService).close("owner-1", "profile deleted");
    }

    @Test
    void aCommandWithoutAnOwnerIsRejectedBeforeTheService() {
        DebitWalletHandler handler = new DebitWalletHandler(walletService, jsonMapper);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> handler.handle("m1", """
                        {"transferId":"%s","amountMinor":3000,
                         "counterpartyAccountNumber":"%s","counterpartyName":"Jane Doe"}
                        """.formatted(TRANSFER, ACCOUNT)))
                .withMessageContaining("has no owner");
        verifyNoInteractions(walletService);
    }

    @Test
    void aCommandWithoutATransferIdIsRejectedBeforeTheService() {
        ReverseDebitHandler handler = new ReverseDebitHandler(walletService, jsonMapper);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> handler.handle("m1", """
                        {"owner":"owner-1","reason":"credit rejected"}
                        """));
        verifyNoInteractions(walletService);
    }

    @Test
    void anInvalidCounterpartyAccountNumberIsRejectedBeforeTheService() {
        CreditWalletHandler handler = new CreditWalletHandler(walletService, jsonMapper);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> handler.handle("m1", """
                        {"owner":"owner-1","transferId":"%s","amountMinor":3000,
                         "counterpartyAccountNumber":"1234","counterpartyName":"Jane Doe"}
                        """.formatted(TRANSFER)));
        verifyNoInteractions(walletService);
    }

    @Test
    void malformedJsonIsRejectedBeforeTheService() {
        DebitWalletHandler handler = new DebitWalletHandler(walletService, jsonMapper);

        assertThatExceptionOfType(JacksonException.class).isThrownBy(() -> handler.handle("m1", "{not json"));
        verifyNoInteractions(walletService);
    }

    @Test
    void aBlankPaymentReferenceIsRejectedBeforeTheService() {
        DepositFundsHandler handler = new DepositFundsHandler(walletService, jsonMapper);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> handler.handle("m1", """
                        {"owner":"owner-1","topUpId":"%s","amountMinor":5000,"paymentReference":" "}
                        """.formatted(TOP_UP)));
        verifyNoInteractions(walletService);
    }
}
