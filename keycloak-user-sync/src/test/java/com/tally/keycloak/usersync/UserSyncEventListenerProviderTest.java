package com.tally.keycloak.usersync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.util.JsonSerialization;

class UserSyncEventListenerProviderTest {

    private static final String REALM_ID = "tally-realm-id";
    private static final String USER_ID = "4f1c2b8e-0000-4000-8000-000000000001";
    private static final String API_KEY = "test-api-key";
    private static final String PATH = "/internal/users/sync";

    record Received(String method, String contentType, String apiKey, String body) {
    }

    private final LinkedBlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private volatile int responseStatus = 200;

    private HttpServer server;
    private URI apiUrl;

    private KeycloakSession session;
    private UserModel user;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("X-Api-Key"),
                    body));
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        server.start();
        apiUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + PATH);

        session = mock(KeycloakSession.class);
        RealmProvider realms = mock(RealmProvider.class);
        UserProvider users = mock(UserProvider.class);
        RealmModel realm = mock(RealmModel.class);
        user = mock(UserModel.class);

        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(realms.getRealm(REALM_ID)).thenReturn(realm);
        when(users.getUserById(realm, USER_ID)).thenReturn(user);

        when(user.getId()).thenReturn(USER_ID);
        when(user.getFirstName()).thenReturn("Ada");
        when(user.getLastName()).thenReturn("Lovelace");
        when(user.getEmail()).thenReturn("ada@example.com");
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+31612345678");
        when(user.getFirstAttribute("address")).thenReturn("Main Street 1, Enschede");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void registerEventSendsOnePostWithProfileAndApiKey() throws Exception {
        provider(apiUrl).onEvent(event(EventType.REGISTER, USER_ID));

        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "expected one POST to the backend");
        assertEquals("POST", request.method());
        assertEquals("application/json", request.contentType());
        assertEquals(API_KEY, request.apiKey());

        Map<String, Object> body = parse(request.body());
        assertEquals(Map.of(
                "subkey", USER_ID,
                "firstName", "Ada",
                "lastName", "Lovelace",
                "email", "ada@example.com",
                "phoneNumber", "+31612345678",
                "address", "Main Street 1, Enschede"), body);

        assertNull(received.poll(200, TimeUnit.MILLISECONDS), "expected exactly one POST");
    }

    @Test
    void missingValuesAreSentAsJsonNull() throws Exception {
        when(user.getLastName()).thenReturn(null);
        when(user.getFirstAttribute("address")).thenReturn(null);

        provider(apiUrl).onEvent(event(EventType.UPDATE_PROFILE, USER_ID));

        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request);
        Map<String, Object> body = parse(request.body());
        assertTrue(body.containsKey("lastName") && body.get("lastName") == null);
        assertTrue(body.containsKey("address") && body.get("address") == null);
        assertEquals(6, body.size());
    }

    @Test
    void unrelatedEventSendsNothing() throws Exception {
        provider(apiUrl).onEvent(event(EventType.LOGOUT, USER_ID));

        assertNull(received.poll(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void eventWithoutUserIdOrUnknownUserSendsNothing() throws Exception {
        UserSyncEventListenerProvider provider = provider(apiUrl);
        provider.onEvent(event(EventType.REGISTER, null));
        provider.onEvent(event(EventType.REGISTER, "unknown-user"));

        assertNull(received.poll(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void missingApiUrlDisablesListener() throws Exception {
        provider(null).onEvent(event(EventType.REGISTER, USER_ID));

        assertNull(received.poll(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void serverErrorDoesNotThrow() throws Exception {
        responseStatus = 500;

        assertDoesNotThrow(() -> provider(apiUrl).onEvent(event(EventType.REGISTER, USER_ID)));
        assertNotNull(received.poll(5, TimeUnit.SECONDS), "request should still have been attempted");
    }

    @Test
    void unreachableBackendDoesNotThrow() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        URI unreachable = URI.create("http://127.0.0.1:" + closedPort + PATH);

        assertDoesNotThrow(() -> provider(unreachable).onEvent(event(EventType.REGISTER, USER_ID)));
    }

    private UserSyncEventListenerProvider provider(URI url) {
        // Send on the calling thread so the tests stay deterministic.
        return new UserSyncEventListenerProvider(session, url, API_KEY, Runnable::run);
    }

    private static Event event(EventType type, String userId) {
        Event event = new Event();
        event.setType(type);
        event.setRealmId(REALM_ID);
        event.setUserId(userId);
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) throws IOException {
        return JsonSerialization.readValue(json, Map.class);
    }
}
