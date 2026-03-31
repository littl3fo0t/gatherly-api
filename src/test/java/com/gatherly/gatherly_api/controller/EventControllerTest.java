package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventAddressResponse;
import com.gatherly.gatherly_api.dto.EventListItemResponse;
import com.gatherly.gatherly_api.dto.EventListResponse;
import com.gatherly.gatherly_api.dto.EventOrganizerResponse;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.dto.RsvpResponse;
import com.gatherly.gatherly_api.dto.OrganizerEventItemResponse;
import com.gatherly.gatherly_api.dto.OrganizerEventListResponse;
import com.gatherly.gatherly_api.dto.UpdateEventRequest;
import com.gatherly.gatherly_api.exception.GlobalExceptionHandler;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.service.EventService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EventControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID EVENT_ID_NOT_FOUND = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID EVENT_ID_ALREADY_FLAGGED = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final UUID EVENT_ID_SOFT_DELETED = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
    private static final UUID EVENT_ID_ALREADY_RSVPED = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EVENT_ID_AT_CAPACITY = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID EVENT_ID_ADMISSIONS_CLOSED = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID EVENT_ID_NO_ACTIVE_RSVP = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final UUID RSVP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

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
                public EventResponse getEventById(UUID eventId, boolean includeOrganizer) {
                    if (UUID.fromString("00000000-0000-0000-0000-0000000000ff").equals(eventId)) {
                        throw new ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                "Event not found."
                        );
                    }
                    return new EventResponse(
                            eventId,
                            "Event Title",
                            "<p>Detail</p>",
                            "in_person",
                            "free",
                            null,
                            OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                            OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                            "America/Toronto",
                            new EventAddressResponse("123 Main St", null, "Toronto", "ON", "M5V 1A1"),
                            "https://example.com/cover.png",
                            7,
                            50,
                            false,
                            List.of("Meetup"),
                            includeOrganizer ? new EventOrganizerResponse(USER_ID, "Jane Doe") : null
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

                @Override
                public OrganizerEventListResponse getMyEvents(
                        UUID organizerId,
                        EventStatus statusFilter,
                        int page,
                        int size
                ) {
                    return new OrganizerEventListResponse(
                            List.of(new OrganizerEventItemResponse(
                                    EVENT_ID,
                                    "My Dashboard Event",
                                    "<p>D</p>",
                                    "in_person",
                                    "free",
                                    null,
                                    null,
                                    OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                                    OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                                    "America/Toronto",
                                    new EventAddressResponse("1 St", null, "Toronto", "ON", "M5V 1A1"),
                                    null,
                                    0,
                                    50,
                                    false,
                                    List.of("Tech"),
                                    EventStatus.active.name(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    new EventOrganizerResponse(USER_ID, "Jane Doe")
                            )),
                            page,
                            size,
                            1,
                            1
                    );
                }

                @Override
                public EventResponse updateEvent(UUID organizerId, UUID eventId, UpdateEventRequest request) {
                    if ("not-owner".equals(request.title())) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You are not the organizer of this event."
                        );
                    }
                    return new EventResponse(
                            eventId,
                            request.title(),
                            request.description(),
                            "in_person",
                            "free",
                            null,
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

                @Override
                public void softDeleteEvent(UUID organizerId, UUID eventId) {
                    if (!EVENT_ID.equals(eventId)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
                    }
                }

                @Override
                public EventResponse restoreEvent(UUID organizerId, UUID eventId) {
                    UUID notSoftDeleted = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
                    if (notSoftDeleted.equals(eventId)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Event is not in a soft deleted state."
                        );
                    }
                    return new EventResponse(
                            eventId,
                            "Restored",
                            "<p>R</p>",
                            "in_person",
                            "free",
                            null,
                            OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                            OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                            "America/Toronto",
                            new EventAddressResponse("1 St", null, "Toronto", "ON", "M5V 1A1"),
                            null,
                            0,
                            50,
                            false,
                            List.of(),
                            new EventOrganizerResponse(USER_ID, "Jane Doe")
                    );
                }

                @Override
                public OrganizerEventItemResponse flagEvent(
                        UUID actorId,
                        Role actorRole,
                        UUID eventId,
                        String reason
                ) {
                    if (EVENT_ID_NOT_FOUND.equals(eventId)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
                    }
                    if (EVENT_ID_SOFT_DELETED.equals(eventId)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Cannot flag a soft-deleted event."
                        );
                    }
                    if (EVENT_ID_ALREADY_FLAGGED.equals(eventId)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Event is already flagged."
                        );
                    }

                    String normalized = reason == null ? null : reason.trim().toLowerCase();
                    boolean valid = normalized != null && List.of(
                            "off_topic", "nsfw", "spam", "misleading", "other"
                    ).contains(normalized);
                    if (!valid) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Missing or invalid flag reason."
                        );
                    }

                    return new OrganizerEventItemResponse(
                            eventId,
                            "Flagged Event",
                            "<p>F</p>",
                            "in_person",
                            "free",
                            null,
                            null,
                            OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                            OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                            "America/Toronto",
                            new EventAddressResponse("1 St", null, "Toronto", "ON", "M5V 1A1"),
                            null,
                            0,
                            50,
                            false,
                            List.of("Tech"),
                            "flagged",
                            normalized,
                            OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                            new EventOrganizerResponse(actorId, "Jane Doe"),
                            null,
                            new EventOrganizerResponse(USER_ID, "Jane Doe")
                    );
                }
            };
        }

        @Bean
        RsvpService rsvpService() {
            return new RsvpService(null, null, null, null) {
                @Override
                public RsvpResponse createRsvp(UUID userId, UUID eventId) {
                    if (EVENT_ID_NOT_FOUND.equals(eventId) || EVENT_ID_SOFT_DELETED.equals(eventId)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
                    }
                    if (EVENT_ID_ALREADY_RSVPED.equals(eventId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "User has already RSVPed for this event.");
                    }
                    if (EVENT_ID_ADMISSIONS_CLOSED.equals(eventId)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Event start time has already passed — admissions closed."
                        );
                    }
                    if (EVENT_ID_AT_CAPACITY.equals(eventId)) {
                        throw new ResponseStatusException(
                                org.springframework.http.HttpStatusCode.valueOf(422),
                                "Event is at maximum capacity."
                        );
                    }
                    return new RsvpResponse(
                            RSVP_ID,
                            eventId,
                            userId,
                            "confirmed",
                            OffsetDateTime.parse("2026-03-31T10:00:00Z"),
                            OffsetDateTime.parse("2026-03-31T10:00:00Z")
                    );
                }

                @Override
                public RsvpResponse cancelRsvp(UUID userId, UUID eventId) {
                    if (EVENT_ID_NOT_FOUND.equals(eventId) || EVENT_ID_SOFT_DELETED.equals(eventId)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
                    }
                    if (EVENT_ID_ADMISSIONS_CLOSED.equals(eventId)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Event start time has already passed — admissions closed."
                        );
                    }
                    if (EVENT_ID_NO_ACTIVE_RSVP.equals(eventId)) {
                        throw new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No active RSVP found for this user and event."
                        );
                    }
                    return new RsvpResponse(
                            RSVP_ID,
                            eventId,
                            userId,
                            "cancelled",
                            OffsetDateTime.parse("2026-03-31T10:00:00Z"),
                            OffsetDateTime.parse("2026-03-31T10:05:00Z")
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
                String role = switch (tokenValue) {
                    case "moderator", "admin", "user" -> tokenValue;
                    default -> "user";
                };
                return Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .subject(USER_ID.toString())
                        .claim("role", role)
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
    void getMyEvents_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/events/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyEvents_withValidToken_returns200() throws Exception {
        mockMvc.perform(get("/api/events/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.content[0].title").value("My Dashboard Event"))
                .andExpect(jsonPath("$.content[0].status").value("active"))
                .andExpect(jsonPath("$.content[0].organizer.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getMyEvents_withStatusQuery_returns200() throws Exception {
        mockMvc.perform(get("/api/events/my?status=flagged&page=1&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void getMyEvents_invalidStatus_returns400Json() throws Exception {
        mockMvc.perform(get("/api/events/my?status=not_a_status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid status filter."));
    }

    @Test
    void getEventById_withoutToken_returns200WithoutOrganizer() throws Exception {
        mockMvc.perform(get("/api/events/" + EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("Event Title"))
                .andExpect(jsonPath("$.organizer").doesNotExist());
    }

    @Test
    void getEventById_withValidToken_returns200WithOrganizer() throws Exception {
        mockMvc.perform(get("/api/events/" + EVENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.organizer.id").value(USER_ID.toString()));
    }

    @Test
    void getEventById_withInvalidToken_treatedAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/events/" + EVENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.organizer").doesNotExist());
    }

    @Test
    void getEventById_missing_returns404Json() throws Exception {
        mockMvc.perform(get("/api/events/00000000-0000-0000-0000-0000000000ff")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/events/00000000-0000-0000-0000-0000000000ff"));
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

    @Test
    void putEvent_withoutToken_returns401() throws Exception {
        String body = """
                {
                  "title": "T",
                  "description": "<p>D</p>",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "1 St",
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "maxCapacity": 50,
                  "categoryIds": []
                }
                """;
        mockMvc.perform(put("/api/events/" + EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putEvent_withValidToken_returns200() throws Exception {
        String body = """
                {
                  "title": "Updated",
                  "description": "<p>D</p>",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "1 St",
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "maxCapacity": 50,
                  "categoryIds": []
                }
                """;
        mockMvc.perform(put("/api/events/" + EVENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.organizer.id").value(USER_ID.toString()));
    }

    @Test
    void putEvent_forbidden_returns403Json() throws Exception {
        String body = """
                {
                  "title": "not-owner",
                  "description": "<p>D</p>",
                  "startTime": "2026-04-01T18:00:00Z",
                  "endTime": "2026-04-01T21:00:00Z",
                  "timezone": "America/Toronto",
                  "addressLine1": "1 St",
                  "city": "Toronto",
                  "province": "ON",
                  "postalCode": "M5V 1A1",
                  "maxCapacity": 50,
                  "categoryIds": []
                }
                """;
        mockMvc.perform(put("/api/events/" + EVENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not the organizer of this event."));
    }

    @Test
    void deleteEvent_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/events/" + EVENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteEvent_withValidToken_returns204() throws Exception {
        mockMvc.perform(delete("/api/events/" + EVENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user"))
                .andExpect(status().isNoContent());
    }

    @Test
    void patchRestoreEvent_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/restore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchRestoreEvent_withValidToken_returns200() throws Exception {
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Restored"));
    }

    @Test
    void patchRestoreEvent_notSoftDeleted_returns400Json() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
        mockMvc.perform(patch("/api/events/" + id + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Event is not in a soft deleted state."));
    }

    @Test
    void patchFlag_withoutToken_returns401() throws Exception {
        String body = """
                {
                  "reason": "off_topic"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/flag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchFlag_userRole_returns403Json() throws Exception {
        String body = """
                {
                  "reason": "off_topic"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void patchFlag_moderatorRole_returns200() throws Exception {
        String body = """
                {
                  "reason": "off_topic"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer moderator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("flagged"))
                .andExpect(jsonPath("$.flagReason").value("off_topic"))
                .andExpect(jsonPath("$.flaggedBy.id").value(USER_ID.toString()));
    }

    @Test
    void patchFlag_invalidReason_returns400Json() throws Exception {
        String body = """
                {
                  "reason": "not_a_real_reason"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer moderator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing or invalid flag reason."));
    }

    @Test
    void patchFlag_eventAlreadyFlagged_returns409Json() throws Exception {
        String body = """
                {
                  "reason": "off_topic"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID_ALREADY_FLAGGED + "/flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer moderator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Event is already flagged."));
    }

    @Test
    void patchFlag_eventNotFound_returns404Json() throws Exception {
        String body = """
                {
                  "reason": "off_topic"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID_NOT_FOUND + "/flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer moderator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Event not found."));
    }

    @Test
    void patchFlag_softDeleted_returns400Json() throws Exception {
        String body = """
                {
                  "reason": "off_topic"
                }
                """;
        mockMvc.perform(patch("/api/events/" + EVENT_ID_SOFT_DELETED + "/flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer moderator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Cannot flag a soft-deleted event."));
    }

    @Test
    void postRsvp_withValidToken_returns201() throws Exception {
        mockMvc.perform(post("/api/events/" + EVENT_ID + "/rsvp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RSVP_ID.toString()))
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-31T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-31T10:00:00Z"));
    }

    @Test
    void postRsvp_missingToken_returns401() throws Exception {
        mockMvc.perform(post("/api/events/" + EVENT_ID + "/rsvp"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postRsvp_alreadyRsvped_returns409Json() throws Exception {
        mockMvc.perform(post("/api/events/" + EVENT_ID_ALREADY_RSVPED + "/rsvp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("User has already RSVPed for this event."));
    }

    @Test
    void postRsvp_atCapacity_returns422Json() throws Exception {
        mockMvc.perform(post("/api/events/" + EVENT_ID_AT_CAPACITY + "/rsvp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Event is at maximum capacity."));
    }

    @Test
    void patchCancelRsvp_withValidToken_returns200() throws Exception {
        mockMvc.perform(patch("/api/events/" + EVENT_ID + "/rsvp/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-31T10:05:00Z"));
    }

    @Test
    void patchCancelRsvp_noActiveRsvp_returns404Json() throws Exception {
        mockMvc.perform(patch("/api/events/" + EVENT_ID_NO_ACTIVE_RSVP + "/rsvp/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No active RSVP found for this user and event."));
    }
}
