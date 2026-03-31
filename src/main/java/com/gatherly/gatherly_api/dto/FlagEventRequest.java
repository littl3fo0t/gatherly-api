package com.gatherly.gatherly_api.dto;

/**
 * Request body for {@code PATCH /api/events/{id}/flag}.
 *
 * <p>The value must be one of the database {@code flag_reason} enum labels:
 * {@code off_topic}, {@code nsfw}, {@code spam}, {@code misleading}, {@code other}.
 *
 * <p>Authorization (moderator/admin) is enforced by the controller/service.
 */
public record FlagEventRequest(String reason) {
}

