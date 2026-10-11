package com.example.walletservice.services;

public interface WalletService {
    void createWallet(String subKey, String idempotencyKey);
}
