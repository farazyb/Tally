package com.example.walletservice.kafka.commands;

public interface CommandHandler {

    void handle(String messageId, String payload);

    CommandType getCommandType();
}
