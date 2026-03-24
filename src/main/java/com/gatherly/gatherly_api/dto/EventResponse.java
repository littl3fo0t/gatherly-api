package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.Profile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full event payload returned by {@code POST /events} and (later) {@code GET /events/{id}}.
 * <p>
 * Field names follow the public API contract (camelCase JSON keys).
 */
public record EventResponse(
        UUID id,
        String title,
        String description,
        String eventType,
        String admissionType,
        BigDecimal admissionFee,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String timezone,
        EventAddressResponse address,
        String coverImageUrl,
        int rsvpCount,
        int maxCapacity,
        boolean isHot,
        List<String> categories,
        EventOrganizerResponse organizer
) {

    /**
     * Builds the JSON shape from a persisted {@link Event} and the category display names.
     * <p>
     * “Hot” means at least 80% of seats are taken — used for sorting on the public listing later.
     * <p>
     * The organizer is normally non-null (the DB enforces {@code organizer_id}), but we avoid
     * assuming that here so a bad row or failed lazy load cannot throw {@link NullPointerException}.
     */
    public static EventResponse from(Event event, List<String> categoryNames) {
        Profile organizer = event.getOrganizer();
        EventOrganizerResponse organizerBlock = organizer == null
                ? null
                : new EventOrganizerResponse(organizer.getId(), organizer.getFullName());
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType().name(),
                event.getAdmissionType().name(),
                event.getAdmissionFee(),
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
                event.getRsvpCount() == null ? 0 : event.getRsvpCount(),
                event.getMaxCapacity(),
                isHot(event.getRsvpCount(), event.getMaxCapacity()),
                List.copyOf(categoryNames),
                organizerBlock
        );
    }

    private static boolean isHot(Integer rsvpCount, int maxCapacity) {
        if (maxCapacity <= 0) {
            return false;
        }
        int count = rsvpCount == null ? 0 : rsvpCount;
        return count * 100L >= maxCapacity * 80L;
    }
}
