package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventListResponse;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    public EventController(EventService eventService) {
        this.eventService = eventService;
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
}
