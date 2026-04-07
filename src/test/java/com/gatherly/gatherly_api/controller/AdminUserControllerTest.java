package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.CorsConfig;
import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.dto.PatchUserRoleRequest;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class AdminUserControllerTest {

    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final AtomicReference<RuntimeException> SERVICE_ERROR = new AtomicReference<>();
    private static final AtomicReference<Profile> SERVICE_RESULT = new AtomicReference<>();

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetStub() {
        SERVICE_ERROR.set(null);
        SERVICE_RESULT.set(null);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AdminUserService adminUserService() {
            return new AdminUserService(null) {
                @Override
                public Profile updateUserRole(UUID targetUserId, PatchUserRoleRequest request) {
                    RuntimeException err = SERVICE_ERROR.get();
                    if (err != null) {
                        throw err;
                    }
                    return SERVICE_RESULT.get();
                }
            };
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return tokenValue -> {
                if ("invalid".equals(tokenValue)) {
                    throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"),
                            "Invalid token"
                    );
                }
                String role = switch (tokenValue) {
                    case "admin" -> "admin";
                    case "user" -> "user";
                    default -> "user";
                };
                Instant now = Instant.now();
                return Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .subject("00000000-0000-0000-0000-000000000099")
                        .claim("role", role)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .build();
            };
        }
    }

    @Test
    void patchRole_withAdminToken_returns200() throws Exception {
        Profile updated = Profile.builder()
                .id(TARGET_USER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.moderator)
                .avatarUrl(null)
                .addressLine1(null)
                .addressLine2(null)
                .city(null)
                .province(null)
                .postalCode(null)
                .createdAt(OffsetDateTime.parse("2026-03-18T10:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-03-18T10:00:00Z"))
                .build();
        SERVICE_RESULT.set(updated);

        mockMvc.perform(patch("/api/admin/users/{id}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"moderator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("moderator"));
    }

    @Test
    void patchRole_withUserToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"moderator\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchRole_missingToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/role", TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"moderator\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchRole_invalidToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"moderator\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchRole_whenNotFound_returns404() throws Exception {
        SERVICE_ERROR.set(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        mockMvc.perform(patch("/api/admin/users/{id}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"moderator\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchRole_whenTargetIsAdmin_returns400() throws Exception {
        SERVICE_ERROR.set(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot change role for an admin account via this endpoint."
        ));

        mockMvc.perform(patch("/api/admin/users/{id}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"moderator\"}"))
                .andExpect(status().isBadRequest());
    }
}
