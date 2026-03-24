package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Province;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /events}.
 * <p>
 * Simple rules (length, required scalars) use Bean Validation annotations. Rules that depend
 * on several fields together — for example “virtual events need a meeting link” — are checked
 * in {@link com.gatherly.gatherly_api.service.EventService} so we can return clear error text.
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank String description,
        @NotNull EventType eventType,
        @NotNull AdmissionType admissionType,
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
        BigDecimal admissionFee,
        @NotNull @Positive Integer maxCapacity,
        @Size(max = 3) List<UUID> categoryIds
) {
}
