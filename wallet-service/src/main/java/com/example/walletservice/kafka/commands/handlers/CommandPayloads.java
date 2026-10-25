package com.example.walletservice.kafka.commands.handlers;

final class CommandPayloads {

    private CommandPayloads() {
    }

    static String requireOwner(String owner, String commandType, String messageId) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException(commandType + " command " + messageId + " has no owner");
        }
        return owner;
    }
}
