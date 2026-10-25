package com.example.walletservice.kafka.commands.handlers;

import com.example.walletservice.domain.TransferId;
import com.example.walletservice.kafka.commands.CommandHandler;
import com.example.walletservice.kafka.commands.CommandType;
import com.example.walletservice.kafka.commands.ReverseDebit;
import com.example.walletservice.services.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static com.example.walletservice.kafka.commands.handlers.CommandPayloads.requireOwner;

@Component
@AllArgsConstructor
@Slf4j
public class ReverseDebitHandler implements CommandHandler {
    private final WalletService walletService;
    private final JsonMapper jsonMapper;

    @Override
    public void handle(String messageId, String payload) {
        ReverseDebit command = jsonMapper.readValue(payload, ReverseDebit.class);
        String owner = requireOwner(command.owner(), getCommandType().value(), messageId);
        log.atDebug().addKeyValue("messageId", messageId).addKeyValue("owner", owner)
                .log("Handling ReverseDebit");
        walletService.reverseDebit(owner, new TransferId(command.transferId()), command.reason());
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.REVERSE_DEBIT;
    }
}
