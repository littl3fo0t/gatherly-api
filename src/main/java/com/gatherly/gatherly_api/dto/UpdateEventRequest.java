package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Province;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PUT /events/{id}}.
 * <p>
 * Same mutable fields as {@link CreateEventRequest}, but without {@code eventType},
 * {@code admissionType}, or {@code admissionFee} — those are fixed at creation (see database triggers).
 * Cross-field rules (virtual needs a link, etc.) live in {@link com.gatherly.gatherly_api.service.EventService}.
 */
public record UpdateEventRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank String description,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotBlank @Size(max = 50) String timezone,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        Province province,
        @Size(max = 7) String postalCode,
        String meetingLink,
        String coverImageUrl,
        @NotNull @Positive Integer maxCapacity,
        @Size(max = 3) List<UUID> categoryIds
) {
}
