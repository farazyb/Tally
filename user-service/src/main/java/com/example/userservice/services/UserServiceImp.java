package com.example.userservice.services;

import com.example.userservice.events.ClientProfileCreatedEvent;
import com.example.userservice.model.Client;
import com.example.userservice.outbox.OutboxWriter;
import com.example.userservice.repository.ClientRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class UserServiceImp implements UserService {
    static final String AGGREGATE_TYPE = "client";
    static final String CLIENT_PROFILE_CREATED = "ClientProfileCreated";

    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");

    private final ClientRepository clientRepository;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional
    public Client getOrCreateClientProfile(String subkey, String firstName, String lastName, String email) {
        Optional<Client> existing = clientRepository.findBySubKey(subkey);
        if (existing.isPresent()) {
            return existing.get();
        }
        Long nextId = clientRepository.nextId();
        if (clientRepository.insertIfAbsent(nextId, subkey, firstName, lastName, email, hashFunction(nextId)) == 1) {
            // Same transaction as the insert: the event exists if and only if the client was committed.
            outboxWriter.write(AGGREGATE_TYPE, subkey, CLIENT_PROFILE_CREATED, new ClientProfileCreatedEvent(subkey, email));
            log.atInfo().addKeyValue("subKey", subkey).log("Client profile created from token (missed sync)");
            SECURITY.atInfo()
                    .addKeyValue("securityEvent", "profile_self_heal")
                    .addKeyValue("subKey", subkey)
                    .log("Client profile created from token claims");
        } else {
            log.atDebug().addKeyValue("subKey", subkey).log("Client profile created concurrently, using it");
        }
        return clientRepository.findBySubKey(subkey).orElseThrow();
    }

    @Override
    @Transactional
    public ClientUpsertResult upsertClientProfile(String subkey, String firstName, String lastName, String email, String phoneNumber, String address) {
        Long nextId = clientRepository.nextId();
        boolean created = clientRepository.upsertBySubKey(nextId, subkey, firstName, lastName, email, phoneNumber, address, hashFunction(nextId));
        Client client = clientRepository.findBySubKey(subkey).orElseThrow();

        if (created) {
            // Same transaction as the upsert: the event exists if and only if the client was committed.
            outboxWriter.write(AGGREGATE_TYPE, subkey, CLIENT_PROFILE_CREATED, new ClientProfileCreatedEvent(subkey, email));
            log.atInfo().addKeyValue("subKey", subkey).log("Client profile created");
        } else {
            // Every login re-syncs the profile, so updates are frequent: debug only.
            log.atDebug().addKeyValue("subKey", subkey).log("Client profile updated");
        }
        return new ClientUpsertResult(client, created);
    }

    public String hashFunction(Long id) {
        final String characters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(characters.charAt((int) (id % characters.length())));
            id /= characters.length();
        }
        return sb.reverse().toString();
    }
}
