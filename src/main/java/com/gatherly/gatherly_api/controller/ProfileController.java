package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.dto.ProfileResponse;
import com.gatherly.gatherly_api.dto.UpdateMyProfileRequest;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profiles", description = "Authenticated profile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user's profile",
            description = "Returns the profile for the authenticated user.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Profile returned successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProfileResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "404", description = "Authenticated profile not found.")
            }
    )
    public ResponseEntity<ProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = readUserIdFromJwt(jwt);
        Profile profile = profileService.getMyProfile(userId);
        return ResponseEntity.ok(ProfileResponse.from(profile));
    }

    @PutMapping("/me")
    @Operation(
            summary = "Update current user's profile",
            description = "Updates editable fields for the authenticated user profile.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Profile updated successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProfileResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Validation failed."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "404", description = "Authenticated profile not found.")
            }
    )
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        UUID userId = readUserIdFromJwt(jwt);
        Profile updated = profileService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ProfileResponse.from(updated));
    }

    /**
     * Converts JWT subject -> UUID.
     * <p>
     * We always use token identity for /me endpoints so clients cannot
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
