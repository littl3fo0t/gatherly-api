package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Profile;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response model for profile endpoints.
 */
public record ProfileResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        String avatarUrl,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getEmail(),
                profile.getRole() == null ? null : profile.getRole().name(),
                profile.getAvatarUrl(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getProvince() == null ? null : profile.getProvince().name(),
                profile.getPostalCode(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
