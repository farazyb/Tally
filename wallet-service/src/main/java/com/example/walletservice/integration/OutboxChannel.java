package com.example.walletservice.integration;

/** Outbox aggregate types. Debezium routes each to the topic {@code wallet.<channel>}. */
public final class OutboxChannel {
    public static final String EVENTS = "events";
    public static final String REPLIES = "replies";

    private OutboxChannel() {
    }
}
