package com.example.walletservice.kafka.commands.handlers;

import com.example.walletservice.kafka.commands.CommandHandler;
import com.example.walletservice.kafka.commands.CommandType;
import com.example.walletservice.kafka.commands.CloseWallet;
import com.example.walletservice.services.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static com.example.walletservice.kafka.commands.handlers.CommandPayloads.requireOwner;

@Component
@AllArgsConstructor
@Slf4j
public class CloseWalletHandler implements CommandHandler {
    private final WalletService walletService;
    private final JsonMapper jsonMapper;

    @Override
    public void handle(String messageId, String payload) {
        CloseWallet command = jsonMapper.readValue(payload, CloseWallet.class);
        String owner = requireOwner(command.owner(), getCommandType().value(), messageId);
        log.atDebug().addKeyValue("messageId", messageId).addKeyValue("owner", owner)
                .log("Handling CloseWallet");
        walletService.close(owner, command.reason());
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.CLOSE_WALLET;
    }
}
