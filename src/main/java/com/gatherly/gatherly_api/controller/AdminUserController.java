package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.dto.PatchUserRoleRequest;
import com.gatherly.gatherly_api.dto.ProfileResponse;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin — Users", description = "Admin-only user management")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PatchMapping("/{id}/role")
    @Operation(
            summary = "Change user role (user or moderator)",
            description = "Promotes or demotes a user between user and moderator. Cannot assign or change the admin role.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Role updated successfully.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProfileResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid role or cannot change admin account."),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
                    @ApiResponse(responseCode = "403", description = "Authenticated user is not admin."),
                    @ApiResponse(responseCode = "404", description = "User not found.")
            }
    )
    public ResponseEntity<ProfileResponse> patchUserRole(
            @Parameter(description = "User ID") @PathVariable("id") UUID id,
            @Valid @RequestBody PatchUserRoleRequest request
    ) {
        Profile updated = adminUserService.updateUserRole(id, request);
        return ResponseEntity.ok(ProfileResponse.from(updated));
    }
}
