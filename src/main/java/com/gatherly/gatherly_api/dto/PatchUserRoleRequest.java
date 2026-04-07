package com.gatherly.gatherly_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Promote or demote a user between user and moderator.")
public record PatchUserRoleRequest(
        @NotNull
        @Schema(
                description = "Target role (cannot be admin).",
                example = "moderator",
                allowableValues = {"user", "moderator"}
        )
        PromotableRole role
) {
}
