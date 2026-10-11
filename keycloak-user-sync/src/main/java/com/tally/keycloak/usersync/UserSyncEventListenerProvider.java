package com.tally.keycloak.usersync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.util.JsonSerialization;

/**
 * Sends the user's profile to the backend after registration, login or a profile update.
 * <p>
 * The payload is built on the request thread, where the Keycloak session is valid; the POST runs on
 * {@code sender} so a slow or unavailable backend never delays the login. Any failure is logged and
 * swallowed. Syncing on every login lets the backend catch up on a sync it missed while it was down.
 */
public class UserSyncEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListenerProvider.class);

    // Compared by name so the code compiles against Keycloak versions that lack some constants.
    private static final Set<String> SYNC_EVENT_TYPES = Set.of("REGISTER", "LOGIN", "UPDATE_PROFILE", "VERIFY_PROFILE");

    static final String ATTR_PHONE_NUMBER = "phoneNumber";
    static final String ATTR_ADDRESS = "address";
    static final String API_KEY_HEADER = "X-Api-Key";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // HTTP/1.1 avoids the h2c upgrade handshake that some plain-HTTP backends mishandle.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();

    private final KeycloakSession session;
    private final URI apiUrl;
    private final String apiKey;
    private final Executor sender;

    /**
     * @param apiUrl backend endpoint; {@code null} disables the listener
     * @param apiKey value for the {@code X-Api-Key} header; omitted when {@code null}
     * @param sender runs the HTTP request
     */
    public UserSyncEventListenerProvider(KeycloakSession session, URI apiUrl, String apiKey, Executor sender) {
        this.session = session;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.sender = sender;
    }

    @Override
    public void onEvent(Event event) {
        if (apiUrl == null || event == null || event.getType() == null
                || !SYNC_EVENT_TYPES.contains(event.getType().name())) {
            return;
        }
        String userId = event.getUserId();
        if (userId == null) {
            return;
        }

        try {
            RealmModel realm = session.realms().getRealm(event.getRealmId());
            UserModel user = realm == null ? null : session.users().getUserById(realm, userId);
            if (user == null) {
                LOG.debugf("User %s not found for event %s, skipping sync", userId, event.getType());
                return;
            }
            String body = JsonSerialization.writeValueAsString(toPayload(user));
            sender.execute(() -> send(userId, body));
        } catch (RejectedExecutionException e) {
            LOG.warnf("User sync queue is full, dropping sync of user %s to %s", userId, apiUrl);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to sync user %s to %s", userId, apiUrl);
        }
    }

    private static UserSyncPayload toPayload(UserModel user) {
        return new UserSyncPayload(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getFirstAttribute(ATTR_PHONE_NUMBER),
                user.getFirstAttribute(ATTR_ADDRESS));
    }

    private void send(String userId, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(apiUrl)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (apiKey != null) {
            request.header(API_KEY_HEADER, apiKey);
        }

        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                LOG.warnf("User sync for %s returned HTTP %d from %s", userId, status, apiUrl);
            } else {
                LOG.debugf("Synced user %s (HTTP %d)", userId, status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.errorf(e, "Interrupted while syncing user %s to %s", userId, apiUrl);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to sync user %s to %s", userId, apiUrl);
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // Admin events are not synced.
    }

    @Override
    public void close() {
        // Nothing to release; the HTTP client and the sender are shared.
    }
}
