package com.example.userservice.api;

import com.example.userservice.api.dto.UserProfileDTO;
import com.example.userservice.model.Client;
import com.example.userservice.model.ContactInformation;
import com.example.userservice.services.ClientUpsertResult;
import com.example.userservice.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/clients")
@AllArgsConstructor
public class UserController {
    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");

    private final UserService userService;

    /**
     * The caller's own profile. The subkey is taken from the token's {@code sub} claim,
     * so a user can never request someone else's profile.
     * <p>
     * If the profile is missing (its sync from Keycloak was missed), it is created from the token's
     * {@code given_name}, {@code family_name} and {@code email} claims; the next Keycloak sync fills in the rest.
     */
    @GetMapping("/me")
    public UserProfileDTO getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return toDto(userService.getOrCreateClientProfile(jwt.getSubject(),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("email")));
    }

    /**
     * Idempotent upsert by {@code subkey}, called by the Keycloak user-sync listener (X-Api-Key required).
     * Returns 201 when the client was created and 200 when it was updated.
     */
    @PostMapping
    public ResponseEntity<UserProfileDTO> upsertUserProfile(@RequestBody UserProfileDTO userProfileDTO,
                                                            HttpServletRequest request) {
        if (userProfileDTO.subkey() == null || userProfileDTO.subkey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subkey is required");
        }
        ClientUpsertResult result = userService.upsertClientProfile(userProfileDTO.subkey(),
                userProfileDTO.firstName(),
                userProfileDTO.lastName(),
                userProfileDTO.email(),
                userProfileDTO.phoneNumber(),
                userProfileDTO.address());

        // Reached only with a valid API key (SyncApiKeyInterceptor): an authorized write of personal data.
        SECURITY.atInfo()
                .addKeyValue("securityEvent", "client_sync")
                .addKeyValue("outcome", result.created() ? "created" : "updated")
                .addKeyValue("subKey", userProfileDTO.subkey())
                .addKeyValue("clientIp", request.getRemoteAddr())
                .log("Client profile synced via API key");

        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(toDto(result.client()));
    }

    private static UserProfileDTO toDto(Client client) {
        ContactInformation contact = client.getContactInformation();
        return new UserProfileDTO(client.getName(),
                client.getSurname(),
                client.getEmail(),
                contact == null ? null : contact.phoneNumber(),
                contact == null ? null : contact.address(),
                client.getSubKey(),
                client.getProfileKey());
    }
}
