package com.example.userservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Only the Keycloak user-sync listener may create or update clients. It authenticates with a shared secret
 * in the {@code X-Api-Key} header. Fails closed: if no key is configured, every request is rejected.
 */
@Slf4j
@Component
public class SyncApiKeyInterceptor implements HandlerInterceptor {
    public static final String API_KEY_HEADER = "X-Api-Key";

    private final byte[] expectedKey;

    public SyncApiKeyInterceptor(@Value("${user-sync.api-key:}") String apiKey) {
        if (apiKey.isBlank()) {
            log.warn("user-sync.api-key is not set; all client upserts will be rejected");
        }
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String provided = request.getHeader(API_KEY_HEADER);
        if (expectedKey.length == 0 || provided == null
                || !MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid " + API_KEY_HEADER);
        }
        return true;
    }
}
