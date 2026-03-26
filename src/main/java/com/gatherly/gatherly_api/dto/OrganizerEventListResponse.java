package com.gatherly.gatherly_api.dto;

import java.util.List;

/**
 * Paginated envelope for {@code GET /api/events/my}.
 */
public record OrganizerEventListResponse(
        List<OrganizerEventItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
