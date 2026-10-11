package com.example.userservice.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes every failed authentication (missing, malformed, expired or invalid bearer token) to the SECURITY log,
 * then responds like the default resource-server entry point (401 with WWW-Authenticate).
 */
@Component
public class AuditingAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");

    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        // The exception message describes why the token was rejected; it never contains the token itself.
        SECURITY.atWarn()
                .addKeyValue("securityEvent", "authentication_failure")
                .addKeyValue("reason", exception.getClass().getSimpleName())
                .addKeyValue("detail", exception.getMessage())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("clientIp", request.getRemoteAddr())
                .log("Authentication failed");
        delegate.commence(request, response, exception);
    }
}
