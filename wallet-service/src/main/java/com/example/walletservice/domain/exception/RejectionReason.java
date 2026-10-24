package com.example.walletservice.domain.exception;

/** Why a wallet command was rejected. */
public enum RejectionReason {
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    WALLET_FROZEN,
    WALLET_CLOSED,
    DEBIT_NOT_FOUND,
    OPERATION_CONFLICT,
    WALLET_NOT_FOUND
}
