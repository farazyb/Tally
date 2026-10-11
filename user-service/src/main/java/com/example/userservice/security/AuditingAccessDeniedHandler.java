package com.example.userservice.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;

/**
 * Writes every access-control rejection of an authenticated caller to the SECURITY log,
 * then responds like the default resource-server handler (403 with WWW-Authenticate).
 */
@Component
public class AuditingAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");

    private final AccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        Principal principal = request.getUserPrincipal();
        SECURITY.atWarn()
                .addKeyValue("securityEvent", "access_denied")
                .addKeyValue("subKey", principal == null ? null : principal.getName())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("clientIp", request.getRemoteAddr())
                .log("Access denied");
        delegate.handle(request, response, exception);
    }
}
