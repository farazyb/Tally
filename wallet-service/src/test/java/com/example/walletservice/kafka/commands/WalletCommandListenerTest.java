package com.example.walletservice.kafka.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletCommandListenerTest {
    private final CommandHandler debit = handlerFor(CommandType.DEBIT_WALLET);
    private final CommandHandler close = handlerFor(CommandType.CLOSE_WALLET);
    private final WalletCommandListener listener = new WalletCommandListener(List.of(debit, close));

    @Test
    void theCommandTypeHeaderSelectsTheHandler() {
        listener.listen("{\"owner\":\"owner-1\"}", "CloseWallet", "m1");

        verify(close).handle("m1", "{\"owner\":\"owner-1\"}");
        verify(debit, never()).handle(anyString(), anyString());
    }

    @Test
    void anUnknownCommandTypeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> listener.listen("{}", "FreezeWallet", "m1"))
                .withMessageContaining("Unknown command type FreezeWallet");
    }

    @Test
    void aMissingCommandTypeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> listener.listen("{}", null, "m1"));
    }

    private static CommandHandler handlerFor(CommandType type) {
        CommandHandler handler = mock(CommandHandler.class);
        when(handler.getCommandType()).thenReturn(type);
        return handler;
    }
}
