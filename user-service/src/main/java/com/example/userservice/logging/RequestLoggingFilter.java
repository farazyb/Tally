package com.example.userservice.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Application access log: one line per HTTP request with method, path, status and duration.
 * Ordered inside the observation filter (HIGHEST_PRECEDENCE + 1), so the line carries the request's traceId,
 * and outside Spring Security, so rejected requests are logged with their 401/403. Actuator calls are skipped.
 * The query string is not logged, since it may carry personal data.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
            log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", elapsedMs(start))
                    .log("HTTP request handled");
        } catch (IOException | ServletException | RuntimeException e) {
            // The exception itself is logged with its stack trace by the servlet container.
            log.atError()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("durationMs", elapsedMs(start))
                    .addKeyValue("error", e.getClass().getSimpleName())
                    .log("HTTP request failed");
            throw e;
        }
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
