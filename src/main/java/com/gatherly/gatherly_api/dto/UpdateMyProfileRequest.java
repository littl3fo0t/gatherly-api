package com.gatherly.gatherly_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating the authenticated user's profile.
 */
public record UpdateMyProfileRequest(
        @NotBlank(message = "fullName is required.")
        @Size(max = 100, message = "fullName must be at most 100 characters.")
        String fullName,

        @Schema(
                description = "Optional. Empty or a valid http or https URL.",
                example = "https://cdn.example.com/avatar.jpg",
                format = "uri",
                nullable = true
        )
        @Pattern(
                regexp = "^(https?://.*)?$",
                message = "avatarUrl must be a valid http/https URL."
        )
        @Size(max = 2000, message = "avatarUrl must be at most 2000 characters.")
        String avatarUrl,

        @Size(max = 255, message = "addressLine1 must be at most 255 characters.")
        String addressLine1,

        @Size(max = 255, message = "addressLine2 must be at most 255 characters.")
        String addressLine2,

        @Size(max = 100, message = "city must be at most 100 characters.")
        String city,

        @Pattern(
                regexp = "^(AB|BC|MB|NB|NL|NS|NT|NU|ON|PE|QC|SK|YT)?$",
                message = "province must be a valid Canadian province/territory code."
        )
        String province,

        @Pattern(
                regexp = "^([A-Za-z]\\d[A-Za-z][ -]?\\d[A-Za-z]\\d)?$",
                message = "postalCode must be a valid Canadian postal code."
        )
        String postalCode
) {
}
