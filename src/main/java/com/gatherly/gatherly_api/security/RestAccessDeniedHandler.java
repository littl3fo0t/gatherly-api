package com.gatherly.gatherly_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Produces a predictable JSON 403 response when an authenticated user
 * does not have sufficient permissions.
 * <p>
 * By default the response only contains safe, generic information.
 * When the active Spring profile is {@code development}, we also add a
 * couple of extra fields to help debugging.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Environment environment;

    public RestAccessDeniedHandler(Environment environment) {
        this.environment = environment;
    }

    private boolean isDevelopmentProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "development".equalsIgnoreCase(p));
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Forbidden");
        body.put("message", "Forbidden");
        body.put("path", request.getRequestURI());

        // More details only during local development (never in production).
        if (isDevelopmentProfileActive()) {
            body.put("exception", accessDeniedException.getClass().getName());
            body.put("debugMessage", accessDeniedException.getMessage());
        }

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

