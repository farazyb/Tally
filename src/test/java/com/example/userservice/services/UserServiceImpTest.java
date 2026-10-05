package com.example.userservice.services;

import com.example.userservice.events.ClientProfileCreatedEvent;
import com.example.userservice.model.Client;
import com.example.userservice.outbox.OutboxWriter;
import com.example.userservice.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class UserServiceImpTest {
    private static final String SUBKEY = "sub-1";
    private static final long NEXT_ID = 100000L;
    private static final String NEXT_PROFILE_KEY = "q0U"; // 100000 in base 62

    private ClientRepository clientRepository;
    private OutboxWriter outboxWriter;
    private UserServiceImp userService;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        outboxWriter = mock(OutboxWriter.class);
        userService = new UserServiceImp(clientRepository, outboxWriter);
        when(clientRepository.nextId()).thenReturn(NEXT_ID);
    }

    @Test
    void newClientIsCreatedWithIdAndProfileKeyAndRecordedInOutbox() {
        Client stored = Client.builder().id(NEXT_ID).subKey(SUBKEY).profileKey(NEXT_PROFILE_KEY).build();
        when(clientRepository.upsertBySubKey(NEXT_ID, SUBKEY, "Ada", "Lovelace", "ada@example.com", "+316", "Main Street 1", NEXT_PROFILE_KEY))
                .thenReturn(true);
        when(clientRepository.findBySubKey(SUBKEY)).thenReturn(Optional.of(stored));

        ClientUpsertResult result = userService.upsertClientProfile(SUBKEY, "Ada", "Lovelace", "ada@example.com", "+316", "Main Street 1");

        assertThat(result.created()).isTrue();
        assertThat(result.client()).isSameAs(stored);
        verify(outboxWriter).write(UserServiceImp.AGGREGATE_TYPE, SUBKEY, UserServiceImp.CLIENT_PROFILE_CREATED,
                new ClientProfileCreatedEvent(SUBKEY, "ada@example.com"));
    }

    @Test
    void existingClientIsUpdatedWithoutEvent() {
        Client stored = Client.builder().id(7L).subKey(SUBKEY).build();
        when(clientRepository.upsertBySubKey(anyLong(), any(), any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(clientRepository.findBySubKey(SUBKEY)).thenReturn(Optional.of(stored));

        ClientUpsertResult result = userService.upsertClientProfile(SUBKEY, "Ada", "King", "ada@example.com", null, null);

        assertThat(result.created()).isFalse();
        verifyNoInteractions(outboxWriter);
    }

    @ParameterizedTest
    @CsvSource({
            "1,      1",
            "61,     Z",
            "62,     10",
            "3843,   ZZ",
            "100000, q0U",
            "3521614606207, ZZZZZZZ" // largest id that fits profile_key varchar(7)
    })
    void hashFunctionEncodesIdInBase62(long id, String expected) {
        assertThat(userService.hashFunction(id)).isEqualTo(expected);
    }
}
