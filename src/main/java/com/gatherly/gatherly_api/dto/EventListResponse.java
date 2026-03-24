package com.gatherly.gatherly_api.dto;

import java.util.List;

/**
 * Paginated envelope for GET /api/events.
 */
public record EventListResponse(
        List<EventListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
