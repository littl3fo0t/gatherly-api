package com.gatherly.gatherly_api.model;

/**
 * Reason a moderator flagged an event ({@code events.flag_reason}).
 * <p>
 * Only relevant when {@link Event#getStatus()} is {@link EventStatus#flagged};
 * otherwise this field stays {@code null} in the database.
 */
public enum FlagReason {
    off_topic,
    nsfw,
    spam,
    misleading,
    other
}
