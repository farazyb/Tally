package com.example.walletservice.kafka.commands.handlers;

import com.example.walletservice.domain.AccountNumber;
import com.example.walletservice.domain.Counterparty;
import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.TransferId;
import com.example.walletservice.kafka.commands.CommandHandler;
import com.example.walletservice.kafka.commands.CommandType;
import com.example.walletservice.kafka.commands.DebitWallet;
import com.example.walletservice.services.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static com.example.walletservice.kafka.commands.handlers.CommandPayloads.requireOwner;

@Component
@AllArgsConstructor
@Slf4j
public class DebitWalletHandler implements CommandHandler {
    private final WalletService walletService;
    private final JsonMapper jsonMapper;

    @Override
    public void handle(String messageId, String payload) {
        DebitWallet command = jsonMapper.readValue(payload, DebitWallet.class);
        String owner = requireOwner(command.owner(), getCommandType().value(), messageId);
        log.atDebug().addKeyValue("messageId", messageId).addKeyValue("owner", owner)
                .log("Handling DebitWallet");
        walletService.debit(owner, new Money(command.amountMinor()), new TransferId(command.transferId()),
                new Counterparty(new AccountNumber(command.counterpartyAccountNumber()), command.counterpartyName()));
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.DEBIT_WALLET;
    }
}
