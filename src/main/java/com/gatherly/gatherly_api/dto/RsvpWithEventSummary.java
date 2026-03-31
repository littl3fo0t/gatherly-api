package com.gatherly.gatherly_api.dto;

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
        String rsvpStatus,
        OffsetDateTime rsvpCreatedAt,
        OffsetDateTime rsvpUpdatedAt,
        UUID eventId,
        String eventTitle,
        String eventType,
        String admissionType,
        BigDecimal admissionFee,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String timezone,
        String city,
        String province,
        String coverImageUrl
) {

    public static RsvpWithEventSummary fromRow(RsvpWithEventSummaryRow row) {
        if (row == null) {
            return null;
        }
        return new RsvpWithEventSummary(
                row.rsvpId(),
                row.rsvpStatus() == null ? null : row.rsvpStatus().name(),
                row.rsvpCreatedAt(),
                row.rsvpUpdatedAt(),
                row.eventId(),
                row.eventTitle(),
                row.eventType() == null ? null : row.eventType().name(),
                row.admissionType() == null ? null : row.admissionType().name(),
                row.admissionFee(),
                row.startTime(),
                row.endTime(),
                row.timezone(),
                row.city(),
                row.province() == null ? null : row.province().name(),
                row.coverImageUrl()
        );
    }
}

