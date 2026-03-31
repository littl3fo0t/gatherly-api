package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.model.RsvpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * RSVP item returned by {@code GET /api/rsvps/my}.
 * <p>
 * Clients typically need RSVP status + a small slice of event fields to render a dashboard list,
 * without paying for the full event detail payload.
 */
public record RsvpWithEventSummary(
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

