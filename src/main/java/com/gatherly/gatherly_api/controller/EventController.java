package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
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
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(
            summary = "Create a new event",
            description = "Creates an event and sets the authenticated user as the organizer.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Event created successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EventResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Validation failed."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "404", description = "Authenticated profile not found.")
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
