package com.example.walletservice.integration;

import java.time.LocalDateTime;


public record WalletCreatedEvent(String owner, LocalDateTime createdAt) {
}
