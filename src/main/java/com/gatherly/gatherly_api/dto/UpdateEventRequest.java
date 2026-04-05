package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Province;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(
        description = "Event type and admission are fixed after creation. The service enforces the same "
                + "location/link rules against the stored event type: virtual and hybrid need meetingLink; "
                + "in_person and hybrid need addressLine1, city, province, and postalCode. When non-blank, "
                + "meetingLink and coverImageUrl must be http or https URLs with a host."
)
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
        @Schema(
                description = "Required for virtual and hybrid events (per stored event type). "
                        + "When provided, must be http or https with a host.",
                example = "https://meet.example.com/room/abc",
                format = "uri"
        )
        String meetingLink,
        @Schema(
                description = "Optional. When provided, must be http or https with a host.",
                example = "https://cdn.example.com/events/banner.jpg",
                format = "uri",
                nullable = true
        )
        String coverImageUrl,
        @NotNull @Positive Integer maxCapacity,
        @Size(max = 3) List<UUID> categoryIds
) {
}
