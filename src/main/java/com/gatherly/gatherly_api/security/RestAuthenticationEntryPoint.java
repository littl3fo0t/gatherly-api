package com.gatherly.gatherly_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/**
 * Produces a predictable JSON 401 response when authentication fails.
 * <p>
 * By default the response only contains safe, generic information.
 * When the active Spring profile is {@code development}, we also add a
 * couple of extra fields to help debugging.
 * <p>
 * Shape matches the structure described in {@code docs/api_endpoints.md}.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Environment environment;

    public RestAuthenticationEntryPoint(Environment environment) {
        this.environment = environment;
    }

    private boolean isDevelopmentProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "development".equalsIgnoreCase(p));
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", "Missing or invalid JWT token.");
        body.put("path", request.getRequestURI());

        // More details only during local development (never in production).
        if (isDevelopmentProfileActive()) {
            body.put("exception", authException.getClass().getName());
            body.put("debugMessage", authException.getMessage());
        }

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

