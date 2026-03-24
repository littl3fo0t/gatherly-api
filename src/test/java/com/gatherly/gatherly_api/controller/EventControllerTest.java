package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventAddressResponse;
import com.gatherly.gatherly_api.dto.EventOrganizerResponse;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.service.EventService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        EventService eventService() {
            return new EventService(null, null, null, null, null) {
                @Override
                public EventResponse createEvent(UUID organizerId, CreateEventRequest request) {
                    return new EventResponse(
                            EVENT_ID,
                            request.title(),
                            request.description(),
                            request.eventType().name(),
                            request.admissionType().name(),
                            request.admissionFee(),
                            request.startTime(),
                            request.endTime(),
                            request.timezone(),
                            new EventAddressResponse(
                                    request.addressLine1(),
                                    request.addressLine2(),
                                    request.city(),
                                    request.province() == null ? null : request.province().name(),
                                    request.postalCode()
                            ),
                            request.coverImageUrl(),
                            0,
                            request.maxCapacity(),
                            false,
                            List.of(),
                            new EventOrganizerResponse(USER_ID, "Jane Doe")
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
                        .subject(USER_ID.toString())
                        .claim("role", "user")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .build();
            };
        }
    }

    @Test
    void postEvents_withValidToken_returns201() throws Exception {
        String body = """
                {
                  "title": "Spring Meetup 2026",
                  "description": "<p>Hi</p>",
                  "eventType": "in_person",
                  "admissionType": "free",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "123 Main St",
                  "addressLine2": null,
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "meetingLink": null,
                  "coverImageUrl": null,
                  "admissionFee": null,
                  "maxCapacity": 50,
                  "categoryIds": []
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("Spring Meetup 2026"))
                .andExpect(jsonPath("$.eventType").value("in_person"))
                .andExpect(jsonPath("$.rsvpCount").value(0))
                .andExpect(jsonPath("$.organizer.fullName").value("Jane Doe"));
    }

    @Test
    void postEvents_missingToken_returns401() throws Exception {
        String body = """
                {
                  "title": "T",
                  "description": "D",
                  "eventType": "in_person",
                  "admissionType": "free",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "1 St",
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "maxCapacity": 10,
                  "categoryIds": []
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postEvents_invalidToken_returns401() throws Exception {
        String body = """
                {
                  "title": "T",
                  "description": "D",
                  "eventType": "in_person",
                  "admissionType": "free",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "1 St",
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "maxCapacity": 10,
                  "categoryIds": []
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postEvents_invalidPayload_returns400() throws Exception {
        String body = """
                {
                  "title": "",
                  "description": "D",
                  "eventType": "in_person",
                  "admissionType": "free",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "1 St",
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "maxCapacity": 10,
                  "categoryIds": []
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
