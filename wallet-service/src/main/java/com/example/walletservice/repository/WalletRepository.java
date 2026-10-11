package com.example.walletservice.repository;

import com.example.walletservice.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    boolean existsBySubKey(String subKey);
}
