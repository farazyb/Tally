package com.example.walletservice.repository;

import com.example.walletservice.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    boolean existsByOwner(String owner);

    @Query("select w from Wallet w left join fetch w.events where w.owner = :owner")
    Optional<Wallet> findByOwnerWithEvents(String owner);
}
