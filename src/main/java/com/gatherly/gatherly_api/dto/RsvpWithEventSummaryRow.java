package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.model.RsvpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Internal projection used by {@code RsvpRepository} JPQL queries.
 * <p>
 * This keeps enum-typed fields so JPQL constructor expressions remain simple and pageable.
 * The public API DTO is {@link RsvpWithEventSummary}, which exposes these as strings for consistency
 * with the rest of the API.
 */
public record RsvpWithEventSummaryRow(
        UUID rsvpId,
        RsvpStatus rsvpStatus,
        OffsetDateTime rsvpCreatedAt,
        OffsetDateTime rsvpUpdatedAt,
        UUID eventId,
        String eventTitle,
        EventType eventType,
        AdmissionType admissionType,
        BigDecimal admissionFee,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String timezone,
        String city,
        Province province,
        String coverImageUrl
) {
}

