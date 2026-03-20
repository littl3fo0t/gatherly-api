package com.gatherly.gatherly_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProtectedHelloController.class)
@Import(com.gatherly.gatherly_api.config.SecurityConfig.class)
class SecurityErrorResponsesTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    @EnableMethodSecurity
    public static class TestJwtDecoderConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            // Test-only JwtDecoder: avoids real JWKS/network calls.
            return tokenValue -> {
                if ("invalid".equals(tokenValue)) {
                    // Throw an OAuth2AuthenticationException so Spring Security treats it as
                    // an authentication failure and routes to AuthenticationEntryPoint.
                    throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"),
                            "Invalid token"
                    );
                }

                String role =
                        "admin".equals(tokenValue) ? "admin" :
                        "user".equals(tokenValue) ? "user" :
                        "user";

                Instant now = Instant.now();
                return Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .subject("test-user")
                        .claim("role", role)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .build();
            };
        }
    }

    @Test
    void missingToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/protected/hello").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing or invalid JWT token."))
                .andExpect(jsonPath("$.path").value("/api/protected/hello"));
    }

    @Test
    void invalidToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/protected/hello")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing or invalid JWT token."))
                .andExpect(jsonPath("$.path").value("/api/protected/hello"));
    }

    @Test
    void userRole_returns403Json() throws Exception {
        // Decoder returns role="user" for tokenValue "user", but the endpoint requires ROLE_ADMIN.
        mockMvc.perform(get("/api/protected/hello")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/protected/hello"));
    }
}

