package com.gatherly.gatherly_api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated envelope used by endpoints that return multiple paged groups.
 * <p>
 * Spring Data's {@link Page} includes many fields, some of which are noisy for clients.
 * This keeps responses consistent with the existing API list shapes.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

