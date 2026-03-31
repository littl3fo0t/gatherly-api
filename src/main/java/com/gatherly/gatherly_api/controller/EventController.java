package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.FlagEventRequest;
import com.gatherly.gatherly_api.dto.EventListResponse;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.dto.RsvpResponse;
import com.gatherly.gatherly_api.dto.UpdateEventRequest;
import com.gatherly.gatherly_api.dto.OrganizerEventListResponse;
import com.gatherly.gatherly_api.dto.OrganizerEventItemResponse;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.service.EventService;
import com.gatherly.gatherly_api.service.RsvpService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for creating and managing events.
 * <p>
 * The authenticated user’s identity always comes from the JWT ({@code sub} claim),
 * not from the JSON body — that prevents clients from forging another user as organizer.
 */
@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Authenticated event endpoints")
public class EventController {

    private final EventService eventService;
    private final RsvpService rsvpService;
    private final JwtDecoder jwtDecoder;

    public EventController(EventService eventService, RsvpService rsvpService, JwtDecoder jwtDecoder) {
        this.eventService = eventService;
        this.rsvpService = rsvpService;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping
    @Operation(
            summary = "List active events",
            description = "Returns a paginated list of active events, sorted with hot events first and then by start time.",
            security = {},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Page of active events.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EventListResponse.class)
                            )
                    )
            }
    )
    public ResponseEntity<EventListResponse> getEvents(
            @Parameter(description = "Zero-based page index. Defaults to 0.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size. Defaults to 25.")
            @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(eventService.getEvents(page, size));
    }

    @GetMapping("/my")
    @Operation(
            summary = "List my events (organizer dashboard)",
            description = "All events you created that are not purged (active, archived, flagged, "
                    + "soft-deleted within the 7-day grace window). Optional status filter. "
                    + "Sorted by start time, newest first.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Page of your events.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = OrganizerEventListResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid `status` query value."
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Missing or invalid token."
                    )
            }
    )
    public ResponseEntity<OrganizerEventListResponse> getMyEvents(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Filter: active, archived, flagged, soft_deleted. Omit for all.")
            @RequestParam(required = false) String status,
            @Parameter(description = "Zero-based page index. Defaults to 0.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size. Defaults to 25.")
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID organizerId = readUserIdFromJwt(jwt);
        EventStatus statusFilter = parseOptionalStatusFilter(status);
        return ResponseEntity.ok(eventService.getMyEvents(organizerId, statusFilter, page, size));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get event details",
            description = "Returns full details for one active event. Organizer is included only when a valid JWT is supplied.",
            security = {},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Event details.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EventResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Event not found or not active."
                    )
            }
    )
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        boolean includeOrganizer = readOptionalUserIdFromAuthorizationHeader(authorizationHeader) != null;
        return ResponseEntity.ok(eventService.getEventById(id, includeOrganizer));
    }

    @PostMapping
    @Operation(
            summary = "Create a new event",
            description = "Creates an event and sets the authenticated user as the organizer.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Event created successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EventResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Validation failed.",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "ValidationError",
                                            value = """
                                                    {
                                                      "timestamp": "2026-03-11T10:00:00Z",
                                                      "status": 400,
                                                      "error": "Bad Request",
                                                      "message": "meetingLink is required for virtual and hybrid events.",
                                                      "path": "/api/events"
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Missing or invalid token.",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "Unauthorized",
                                            value = """
                                                    {
                                                      "timestamp": "2026-03-11T10:00:00Z",
                                                      "status": 401,
                                                      "error": "Unauthorized",
                                                      "message": "Missing or invalid JWT token.",
                                                      "path": "/api/events"
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Authenticated profile not found.",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "ProfileMissing",
                                            value = """
                                                    {
                                                      "timestamp": "2026-03-11T10:00:00Z",
                                                      "status": 404,
                                                      "error": "Not Found",
                                                      "message": "Profile not found for authenticated user.",
                                                      "path": "/api/events"
                                                    }
                                                    """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<EventResponse> createEvent(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateEventRequest request
    ) {
        UUID organizerId = readUserIdFromJwt(jwt);
        EventResponse body = eventService.createEvent(organizerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/rsvp")
    @Operation(
            summary = "RSVP to an event",
            description = "Creates a confirmed RSVP for the authenticated user on the specified event.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "RSVP confirmed.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = RsvpResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Event start time has already passed — admissions closed."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "404", description = "Event not found or not active."),
                    @ApiResponse(responseCode = "409", description = "User has already RSVPed for this event."),
                    @ApiResponse(responseCode = "422", description = "Event is at maximum capacity.")
            }
    )
    public ResponseEntity<RsvpResponse> createRsvp(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        UUID userId = readUserIdFromJwt(jwt);
        RsvpResponse body = rsvpService.createRsvp(userId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}/rsvp/cancel")
    @Operation(
            summary = "Cancel my RSVP",
            description = "Cancels the authenticated user's RSVP for the specified event.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "RSVP cancelled successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = RsvpResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Event start time has already passed."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "404", description = "No active RSVP found for this user and event.")
            }
    )
    public ResponseEntity<RsvpResponse> cancelRsvp(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        UUID userId = readUserIdFromJwt(jwt);
        return ResponseEntity.ok(rsvpService.cancelRsvp(userId, id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an event",
            description = "Updates mutable fields for an event you own. "
                    + "Admission and event type cannot change. "
                    + "maxCapacity cannot decrease.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Event updated.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EventResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Validation failed or event is soft deleted."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "403", description = "Authenticated user is not the organizer."),
                    @ApiResponse(responseCode = "404", description = "Event not found."),
                    @ApiResponse(responseCode = "409", description = "maxCapacity would decrease.")
            }
    )
    public ResponseEntity<EventResponse> updateEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        UUID organizerId = readUserIdFromJwt(jwt);
        return ResponseEntity.ok(eventService.updateEvent(organizerId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Soft delete an event",
            description = "Sets the event to soft_deleted and records deleted_at. Only the organizer may delete.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Soft deleted (or already deleted)."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "403", description = "Authenticated user is not the organizer."),
                    @ApiResponse(responseCode = "404", description = "Event not found.")
            }
    )
    public ResponseEntity<Void> softDeleteEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        UUID organizerId = readUserIdFromJwt(jwt);
        eventService.softDeleteEvent(organizerId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @Operation(
            summary = "Restore a soft deleted event",
            description = "Within the 7-day grace window, clears soft delete and sets status to active or archived "
                    + "based on whether end time has passed.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Event restored.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EventResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Event is not soft deleted."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "403", description = "Authenticated user is not the organizer."),
                    @ApiResponse(responseCode = "404", description = "Event not found or grace period expired.")
            }
    )
    public ResponseEntity<EventResponse> restoreEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        UUID organizerId = readUserIdFromJwt(jwt);
        return ResponseEntity.ok(eventService.restoreEvent(organizerId, id));
    }

    @PatchMapping("/{id}/flag")
    @Operation(
            summary = "Flag an event",
            description = "Flags an event as inappropriate. Restricted to moderator/admin roles only.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Event flagged successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = OrganizerEventItemResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Missing or invalid flag reason."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "403", description = "Authenticated user does not have moderator or admin role."),
                    @ApiResponse(responseCode = "404", description = "Event not found."),
                    @ApiResponse(responseCode = "409", description = "Event is already flagged.")
            }
    )
    public ResponseEntity<OrganizerEventItemResponse> flagEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody FlagEventRequest request
    ) {
        UUID actorId = readUserIdFromJwt(jwt);
        Role actorRole = parseUserRoleFromJwt(jwt);
        if (actorRole == null || (actorRole != Role.moderator && actorRole != Role.admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(eventService.flagEvent(actorId, actorRole, id, request == null ? null : request.reason()));
    }

    /**
     * Reads the application role from the JWT.
     *
     * <p>We mimic {@code SecurityConfig} for consistency:
     * check app_metadata.role → user_metadata.role → top-level role (excluding "authenticated"/"anon").
     */
    private static Role parseUserRoleFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        Object appMetadataClaim = jwt.getClaim("app_metadata");
        if (appMetadataClaim instanceof Map<?, ?> appMetadataMap) {
            Object r = appMetadataMap.get("role");
            if (r instanceof String s && !s.isBlank()) {
                return toRole(s);
            }
        }

        Object userMetadataClaim = jwt.getClaim("user_metadata");
        if (userMetadataClaim instanceof Map<?, ?> userMetadataMap) {
            Object r = userMetadataMap.get("role");
            if (r instanceof String s && !s.isBlank()) {
                return toRole(s);
            }
        }

        String top = jwt.getClaimAsString("role");
        if (top == null || top.isBlank()
                || "authenticated".equalsIgnoreCase(top)
                || "anon".equalsIgnoreCase(top)) {
            return null;
        }
        return toRole(top);
    }

    private static Role toRole(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Reads the Supabase user id from the JWT subject (standard {@code sub} claim).
     */
    private UUID readUserIdFromJwt(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user subject in token.");
        }
    }

    /**
     * Optional {@code status} for GET /my; must match {@link EventStatus} enum names (e.g. {@code soft_deleted}).
     */
    private static EventStatus parseOptionalStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EventStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter.");
        }
    }

    /**
     * Best-effort JWT parsing for public routes: invalid tokens are treated as anonymous callers.
     */
    private UUID readOptionalUserIdFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String tokenValue = authorizationHeader.substring("Bearer ".length()).trim();
        if (tokenValue.isEmpty()) {
            return null;
        }
        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                return null;
            }
            return UUID.fromString(subject);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
