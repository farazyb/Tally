package com.example.walletservice.kafka;

import java.util.Arrays;
import java.util.Optional;

public enum EventType {
    CLIENT_PROFILE_CREATED("ClientProfileCreated"),
    CLIENT_PROFILE_DELETED("ClientProfileDeleted");
    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public static Optional<EventType> from(String v) {
        return Arrays.stream(values()).filter(t -> t.value.equals(v)).findFirst();
    }
}
