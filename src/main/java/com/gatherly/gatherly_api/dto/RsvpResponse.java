package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Rsvp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JSON shape for an RSVP record.
 */
public record RsvpResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RsvpResponse from(Rsvp rsvp) {
        return new RsvpResponse(
                rsvp.getId(),
                rsvp.getEvent() == null ? null : rsvp.getEvent().getId(),
                rsvp.getUser() == null ? null : rsvp.getUser().getId(),
                rsvp.getStatus() == null ? null : rsvp.getStatus().name(),
                rsvp.getCreatedAt(),
                rsvp.getUpdatedAt()
        );
    }
}

