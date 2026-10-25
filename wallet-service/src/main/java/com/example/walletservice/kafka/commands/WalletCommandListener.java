package com.example.walletservice.kafka.commands;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes wallet commands: the "commandType" header selects the handler, the "id" header is the message id.
 * A missing or unknown command type is rejected, so the record goes to the dead-letter topic.
 */
@Component
@AllArgsConstructor
@Slf4j
public class WalletCommandListener {
    private final List<CommandHandler> handlers;

    @KafkaListener(topics = "wallet.commands")
    public void listen(@Payload String data,
                       @Header(name = "commandType", required = false) String commandType,
                       @Header(name = "id", required = false) String messageId) {
        log.info("Received command {} of type {}", messageId, commandType);
        CommandHandler handler = CommandType.from(commandType)
                .flatMap(type -> handlers.stream()
                        .filter(h -> h.getCommandType() == type)
                        .findFirst())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown command type " + commandType + " in message " + messageId));
        handler.handle(messageId, data);
    }
}
