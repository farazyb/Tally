package com.example.walletservice.services;

import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.TopUpId;
import com.example.walletservice.domain.TransferId;

public interface WalletService {
    void createWallet(String owner, String creationRequestId);

    void debit(String owner, Money amount, TransferId transferId, Counterparty counterparty);

    void credit(String owner, Money amount, TransferId transferId, Counterparty counterparty);

    void reverseDebit(String owner, TransferId transferId, String reason);

    void deposit(String owner, Money amount, TopUpId topUpId, PaymentReference paymentReference);

    void close(String owner, String reason);
}
