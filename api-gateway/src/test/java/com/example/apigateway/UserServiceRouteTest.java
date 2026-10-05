package com.example.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserServiceRouteTest {

    private static final String TOKEN = "valid-token";
    private static final String BEARER = "Bearer " + TOKEN;

    private static final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
    private static final HttpServer userService = startFakeUserService();

    @DynamicPropertySource
    static void routeToFakeUserService(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:" + userService.getAddress().getPort());
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        received.clear();
        when(jwtDecoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll
    static void stopFakeUserService() {
        userService.stop(0);
    }

    @Test
    void meIsForwardedWithTheBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/clients/me").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk());

        // user-service validates the same token and reads the subject from it.
        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("GET /api/v1/clients/me " + BEARER);
    }

    @Test
    void otherClientPathsAreNotForwarded() throws Exception {
        mockMvc.perform(get("/api/v1/clients/someone-elses-subkey").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().is4xxClientError());

        assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void postIsNotForwardedEvenWithValidToken() throws Exception {
        mockMvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("X-Api-Key", "guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subkey\":\"someone-else\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/clients/me"))
                .andExpect(status().isUnauthorized());

        assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    private static HttpServer startFakeUserService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                        + " " + exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
