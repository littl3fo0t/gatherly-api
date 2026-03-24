package com.gatherly.gatherly_api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard JSON shape for API error responses.
 * <p>
 * Most errors use the top-level fields. Validation errors can additionally include
 * field-specific items inside {@code errors}.
 */
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ApiFieldError> errors
) {
}
