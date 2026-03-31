package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.dto.MyRsvpsResponse;
import com.gatherly.gatherly_api.service.RsvpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/rsvps")
@Tag(name = "RSVPs", description = "Authenticated RSVP endpoints")
@SecurityRequirement(name = "bearerAuth")
public class RsvpController {

    private final RsvpService rsvpService;

    public RsvpController(RsvpService rsvpService) {
        this.rsvpService = rsvpService;
    }

    @GetMapping("/my")
    @Operation(
            summary = "List my RSVPs",
            description = "Returns the authenticated user's RSVPs split into upcoming and past. "
                    + "Upcoming vs past is based on the event start time compared to now (UTC).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Returns paginated upcoming and past RSVP lists.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = MyRsvpsResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid status filter."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token.")
            }
    )
    public ResponseEntity<MyRsvpsResponse> getMyRsvps(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Filter by RSVP status: confirmed or cancelled. Omit for all.")
            @RequestParam(required = false) String status,
            @Parameter(description = "Zero-based page index. Defaults to 0.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size. Defaults to 25.")
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID userId = readUserIdFromJwt(jwt);
        return ResponseEntity.ok(rsvpService.getMyRsvps(userId, status, page, size));
    }

    /**
     * Converts JWT subject -> UUID.
     * <p>
     * We always use token identity for /my endpoints so clients cannot
     * impersonate other users by passing arbitrary IDs in request data.
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

