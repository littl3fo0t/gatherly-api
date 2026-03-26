package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.Profile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full event row for the authenticated organizer's dashboard ({@code GET /api/events/my}).
 * <p>
 * Includes lifecycle fields that public listings hide (status, flag metadata, soft-delete time).
 */
public record OrganizerEventItemResponse(
        UUID id,
        String title,
        String description,
        String eventType,
        String admissionType,
        BigDecimal admissionFee,
        String meetingLink,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String timezone,
        EventAddressResponse address,
        String coverImageUrl,
        int rsvpCount,
        int maxCapacity,
        boolean isHot,
        List<String> categories,
        String status,
        String flagReason,
        OffsetDateTime flaggedAt,
        EventOrganizerResponse flaggedBy,
        OffsetDateTime deletedAt,
        EventOrganizerResponse organizer
) {

    public static OrganizerEventItemResponse from(Event event, List<String> categoryNames) {
        Profile organizer = event.getOrganizer();
        EventOrganizerResponse organizerBlock = organizer == null
                ? null
                : new EventOrganizerResponse(organizer.getId(), organizer.getFullName());

        Profile flaggedByProfile = event.getFlaggedBy();
        EventOrganizerResponse flaggedByBlock = flaggedByProfile == null
                ? null
                : new EventOrganizerResponse(flaggedByProfile.getId(), flaggedByProfile.getFullName());

        int rsvp = event.getRsvpCount() == null ? 0 : event.getRsvpCount();
        int cap = event.getMaxCapacity() == null ? 0 : event.getMaxCapacity();

        return new OrganizerEventItemResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType().name(),
                event.getAdmissionType().name(),
                event.getAdmissionFee(),
                event.getMeetingLink(),
                event.getStartTime(),
                event.getEndTime(),
                event.getTimezone(),
                new EventAddressResponse(
                        event.getAddressLine1(),
                        event.getAddressLine2(),
                        event.getCity(),
                        event.getProvince() == null ? null : event.getProvince().name(),
                        event.getPostalCode()
                ),
                event.getCoverImageUrl(),
                rsvp,
                cap,
                isHot(rsvp, cap),
                List.copyOf(categoryNames),
                event.getStatus().name(),
                event.getFlagReason() == null ? null : event.getFlagReason().name(),
                event.getFlaggedAt(),
                flaggedByBlock,
                event.getDeletedAt(),
                organizerBlock
        );
    }

    private static boolean isHot(int rsvpCount, int maxCapacity) {
        if (maxCapacity <= 0) {
            return false;
        }
        return rsvpCount * 100L >= maxCapacity * 80L;
    }
}
