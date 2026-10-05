package com.example.userservice.services;

import com.example.userservice.model.Client;

public record ClientUpsertResult(Client client, boolean created) {
}
