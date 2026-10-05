package com.example.userservice.services;

import com.example.userservice.model.Client;

import java.util.Optional;


public interface UserService {
     Optional<Client> findClientProfile(String subkey);

    /**
     * Creates the client for {@code subkey}, or updates it if it already exists.
     */
     ClientUpsertResult upsertClientProfile(String subkey, String firstName, String lastName, String email, String phoneNumber, String address);



}
