package com.gatherly.gatherly_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code PATCH /api/events/{id}/flag}.
 *
 * <p>The value must be one of the database {@code flag_reason} enum labels:
 * {@code off_topic}, {@code nsfw}, {@code spam}, {@code misleading}, {@code other}.
 *
 * <p>Authorization (moderator/admin) is enforced by the controller/service.
 */
public record FlagEventRequest(
        @Schema(
                description = "Flag reason label (lowercase, as stored in the database).",
                example = "spam",
                allowableValues = { "off_topic", "nsfw", "spam", "misleading", "other" }
        )
        String reason
) {
}

