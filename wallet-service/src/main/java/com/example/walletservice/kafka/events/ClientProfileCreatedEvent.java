package com.example.walletservice.kafka.events;

/** Payload of user-service's ClientProfileCreated event. */
public record ClientProfileCreatedEvent(String subKey, String email) {
}
