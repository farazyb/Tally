package com.tally.keycloak.usersync;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON body sent to the backend. Keycloak's shared ObjectMapper drops null fields by default,
 * so inclusion is forced here to always send every field, with missing values as JSON null.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserSyncPayload(
        String subkey,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String address) {
}
