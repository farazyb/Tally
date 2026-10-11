package com.example.walletservice.events;

import java.time.LocalDateTime;

public record WalletCreatedEvent(String subKey, LocalDateTime createdAt) {
}
