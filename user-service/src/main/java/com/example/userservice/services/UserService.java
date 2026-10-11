package com.example.userservice.services;

import com.example.userservice.model.Client;


public interface UserService {
    /**
     * Returns the client for {@code subkey}, creating it from the given token claims if it does not exist.
     * Self-heals profiles whose sync from Keycloak was missed (e.g. user-service was down at registration).
     */
    Client getOrCreateClientProfile(String subkey, String firstName, String lastName, String email);

    /**
     * Creates the client for {@code subkey}, or updates it if it already exists.
     */
     ClientUpsertResult upsertClientProfile(String subkey, String firstName, String lastName, String email, String phoneNumber, String address);



}
