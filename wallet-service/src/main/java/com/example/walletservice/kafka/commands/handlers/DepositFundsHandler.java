package com.example.walletservice.kafka.commands.handlers;

import com.example.walletservice.domain.Money;
import com.example.walletservice.domain.PaymentReference;
import com.example.walletservice.domain.TopUpId;
import com.example.walletservice.kafka.commands.CommandHandler;
import com.example.walletservice.kafka.commands.CommandType;
import com.example.walletservice.kafka.commands.DepositFunds;
import com.example.walletservice.services.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static com.example.walletservice.kafka.commands.handlers.CommandPayloads.requireOwner;

@Component
@AllArgsConstructor
@Slf4j
public class DepositFundsHandler implements CommandHandler {
    private final WalletService walletService;
    private final JsonMapper jsonMapper;

    @Override
    public void handle(String messageId, String payload) {
        DepositFunds command = jsonMapper.readValue(payload, DepositFunds.class);
        String owner = requireOwner(command.owner(), getCommandType().value(), messageId);
        log.atDebug().addKeyValue("messageId", messageId).addKeyValue("owner", owner)
                .log("Handling DepositFunds");
        walletService.deposit(owner, new Money(command.amountMinor()), new TopUpId(command.topUpId()),
                new PaymentReference(command.paymentReference()));
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.DEPOSIT_FUNDS;
    }
}
