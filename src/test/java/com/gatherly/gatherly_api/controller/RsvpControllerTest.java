package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.CorsConfig;
import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.dto.MyRsvpsResponse;
import com.gatherly.gatherly_api.dto.PageResponse;
import com.gatherly.gatherly_api.dto.RsvpWithEventSummary;
import com.gatherly.gatherly_api.exception.GlobalExceptionHandler;
import com.gatherly.gatherly_api.service.RsvpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RsvpController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class RsvpControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final AtomicReference<String> LAST_STATUS = new AtomicReference<>(null);
    private static final AtomicReference<Integer> LAST_PAGE = new AtomicReference<>(null);
    private static final AtomicReference<Integer> LAST_SIZE = new AtomicReference<>(null);

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        RsvpService rsvpService() {
            return new RsvpService(null, null, null, null) {
                @Override
                public MyRsvpsResponse getMyRsvps(UUID userId, String statusFilter, int page, int size) {
                    LAST_STATUS.set(statusFilter);
                    LAST_PAGE.set(page);
                    LAST_SIZE.set(size);

                    if ("not_a_status".equals(statusFilter)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter.");
                    }

                    RsvpWithEventSummary upcomingItem = new RsvpWithEventSummary(
                            UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
                            "confirmed",
                            OffsetDateTime.parse("2026-03-30T10:00:00Z"),
                            OffsetDateTime.parse("2026-03-30T10:00:00Z"),
                            UUID.fromString("00000000-0000-0000-0000-0000000000c1"),
                            "Upcoming Event",
                            "in_person",
                            "free",
                            null,
                            OffsetDateTime.parse("2026-04-10T18:00:00Z"),
                            OffsetDateTime.parse("2026-04-10T21:00:00Z"),
                            "America/Toronto",
                            "Toronto",
                            "ON",
                            null
                    );

                    RsvpWithEventSummary pastItem = new RsvpWithEventSummary(
                            UUID.fromString("00000000-0000-0000-0000-0000000000b2"),
                            "cancelled",
                            OffsetDateTime.parse("2026-03-01T10:00:00Z"),
                            OffsetDateTime.parse("2026-03-02T10:00:00Z"),
                            UUID.fromString("00000000-0000-0000-0000-0000000000c2"),
                            "Past Event",
                            "virtual",
                            "paid",
                            java.math.BigDecimal.valueOf(10.00),
                            OffsetDateTime.parse("2026-03-05T18:00:00Z"),
                            OffsetDateTime.parse("2026-03-05T21:00:00Z"),
                            "America/Toronto",
                            null,
                            null,
                            "https://example.com/cover.png"
                    );

                    return new MyRsvpsResponse(
                            new PageResponse<>(List.of(upcomingItem), page, size, 1, 1),
                            new PageResponse<>(List.of(pastItem), page, size, 1, 1)
                    );
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
    void getMyRsvps_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/rsvps/my").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyRsvps_withToken_returns200AndTwoGroups() throws Exception {
        mockMvc.perform(get("/api/rsvps/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.page").value(0))
                .andExpect(jsonPath("$.past.page").value(0))
                .andExpect(jsonPath("$.upcoming.content[0].eventTitle").value("Upcoming Event"))
                .andExpect(jsonPath("$.past.content[0].eventTitle").value("Past Event"))
                .andExpect(jsonPath("$.upcoming.content[0].rsvpStatus").value("confirmed"))
                .andExpect(jsonPath("$.past.content[0].rsvpStatus").value("cancelled"));
    }

    @Test
    void getMyRsvps_invalidStatus_returns400Json() throws Exception {
        mockMvc.perform(get("/api/rsvps/my?status=not_a_status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid status filter."));
    }

    @Test
    void getMyRsvps_withQueryParams_passesThrough() throws Exception {
        mockMvc.perform(get("/api/rsvps/my?status=confirmed&page=2&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.page").value(2))
                .andExpect(jsonPath("$.upcoming.size").value(10))
                .andExpect(jsonPath("$.past.page").value(2))
                .andExpect(jsonPath("$.past.size").value(10));

        org.junit.jupiter.api.Assertions.assertEquals("confirmed", LAST_STATUS.get());
        org.junit.jupiter.api.Assertions.assertEquals(2, LAST_PAGE.get());
        org.junit.jupiter.api.Assertions.assertEquals(10, LAST_SIZE.get());
    }
}

