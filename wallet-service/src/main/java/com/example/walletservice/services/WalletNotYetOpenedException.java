package com.example.walletservice.services;

/** A command arrived for an owner whose wallet does not exist yet. */
public class WalletNotYetOpenedException extends RuntimeException {

    public WalletNotYetOpenedException(String owner) {
        super("No wallet for owner " + owner + " yet");
    }
}
