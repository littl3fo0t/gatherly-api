package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.CorsConfig;
import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.dto.UpdateMyProfileRequest;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class ProfileControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicReference<Profile> PROFILE = new AtomicReference<>(null);

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        ProfileService profileService() {
            return new ProfileService(null) {
                @Override
                public Profile getMyProfile(UUID userId) {
                    Profile current = PROFILE.get();
                    if (current == null) {
                        throw new ResponseStatusException(NOT_FOUND, "Profile not found for authenticated user.");
                    }
                    return current;
                }

                @Override
                public Profile updateMyProfile(UUID userId, UpdateMyProfileRequest request) {
                    Profile current = getMyProfile(userId);
                    current.applySelfServiceUpdate(
                            request.fullName(),
                            request.avatarUrl(),
                            request.addressLine1(),
                            request.addressLine2(),
                            request.city(),
                            request.province(),
                            request.postalCode()
                    );
                    PROFILE.set(current);
                    return current;
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

                Instant now = Instant.now();
                return Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .subject(TEST_USER_ID.toString())
                        .claim("role", "user")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .build();
            };
        }
    }

    @Test
    void getMe_withValidToken_returns200() throws Exception {
        PROFILE.set(seedProfile());

        mockMvc.perform(get("/api/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("user"));
    }

    @Test
    void putMe_withValidTokenAndBody_returns200() throws Exception {
        PROFILE.set(seedProfile());

        String body = """
                {
                  "fullName": "Jane Smith",
                  "avatarUrl": "https://res.cloudinary.com/example/avatar.png",
                  "addressLine1": "456 Queen St",
                  "addressLine2": "Unit 8",
                  "city": "Ottawa",
                  "province": "ON",
                  "postalCode": "K1A 0A9"
                }
                """;

        mockMvc.perform(put("/api/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.city").value("Ottawa"))
                .andExpect(jsonPath("$.province").value("ON"))
                .andExpect(jsonPath("$.postalCode").value("K1A 0A9"));
    }

    @Test
    void getMe_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/profiles/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putMe_invalidPayload_returns400() throws Exception {
        PROFILE.set(seedProfile());

        String body = """
                {
                  "fullName": "",
                  "province": "XX",
                  "postalCode": "not-valid"
                }
                """;

        mockMvc.perform(put("/api/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private static Profile seedProfile() {
        return Profile.builder()
                .id(TEST_USER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.user)
                .avatarUrl(null)
                .addressLine1("123 King St")
                .addressLine2(null)
                .city("Toronto")
                .province(Province.ON)
                .postalCode("M5V 2T6")
                .createdAt(OffsetDateTime.parse("2026-03-18T10:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-03-18T10:00:00Z"))
                .build();
    }
}
