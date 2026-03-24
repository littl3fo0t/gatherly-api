package com.gatherly.gatherly_api.dto;

import com.gatherly.gatherly_api.model.Event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight event row used by GET /api/events list responses.
 */
public record EventListItemResponse(
        UUID id,
        String title,
        String eventType,
        String admissionType,
        BigDecimal admissionFee,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String timezone,
        String city,
        String province,
        String coverImageUrl,
        int rsvpCount,
        int maxCapacity,
        boolean isHot,
        List<String> categories
) {
    public static EventListItemResponse from(Event event, List<String> categories) {
        int rsvpCount = event.getRsvpCount() == null ? 0 : event.getRsvpCount();
        int maxCapacity = event.getMaxCapacity() == null ? 0 : event.getMaxCapacity();
        return new EventListItemResponse(
                event.getId(),
                event.getTitle(),
                event.getEventType().name(),
                event.getAdmissionType().name(),
                event.getAdmissionFee(),
                event.getStartTime(),
                event.getEndTime(),
                event.getTimezone(),
                event.getCity(),
                event.getProvince() == null ? null : event.getProvince().name(),
                event.getCoverImageUrl(),
                rsvpCount,
                maxCapacity,
                isHot(rsvpCount, maxCapacity),
                List.copyOf(categories)
        );
    }

    private static boolean isHot(int rsvpCount, int maxCapacity) {
        if (maxCapacity <= 0) {
            return false;
        }
        return rsvpCount * 100L >= maxCapacity * 80L;
    }
}
