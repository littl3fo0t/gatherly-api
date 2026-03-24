package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventAddressResponse;
import com.gatherly.gatherly_api.dto.EventListItemResponse;
import com.gatherly.gatherly_api.dto.EventListResponse;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                public EventListResponse getEvents(int page, int size) {
                    return new EventListResponse(
                            List.of(new EventListItemResponse(
                                    EVENT_ID,
                                    "Spring Meetup 2026",
                                    "in_person",
                                    "free",
                                    null,
                                    OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                                    OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                                    "America/Toronto",
                                    "Toronto",
                                    "ON",
                                    null,
                                    42,
                                    50,
                                    true,
                                    List.of("Meetup", "Tech")
                            )),
                            page,
                            size,
                            1,
                            1
                    );
                }

                @Override
                public EventResponse createEvent(UUID organizerId, CreateEventRequest request) {
                    if ("trigger-service-400".equals(request.title())) {
                        throw new ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST,
                                "meetingLink is required for virtual and hybrid events."
                        );
                    }
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
    void getEvents_withoutToken_returns200() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Spring Meetup 2026"))
                .andExpect(jsonPath("$.content[0].isHot").value(true))
                .andExpect(jsonPath("$.content[0].categories[0]").value("Meetup"));
    }

    @Test
    void getEvents_withQueryParams_returnsRequestedPageAndSize() throws Exception {
        mockMvc.perform(get("/api/events?page=2&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
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
                        .accept(MediaType.TEXT_HTML)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.path").value("/api/events"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void postEvents_serviceRuleViolation_returns400Json() throws Exception {
        String body = """
                {
                  "title": "trigger-service-400",
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
                        .accept(MediaType.TEXT_HTML)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("meetingLink is required for virtual and hybrid events."))
                .andExpect(jsonPath("$.path").value("/api/events"));
    }
}
