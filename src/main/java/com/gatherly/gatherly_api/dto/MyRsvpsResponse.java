package com.gatherly.gatherly_api.dto;

/**
 * Response body for {@code GET /api/rsvps/my}.
 * <p>
 * We return two independent paged groups (upcoming and past) so clients can render
 * two sections without making two HTTP calls.
 */
public record MyRsvpsResponse(
        PageResponse<RsvpWithEventSummary> upcoming,
        PageResponse<RsvpWithEventSummary> past
) {
}

