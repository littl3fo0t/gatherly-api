package com.gatherly.gatherly_api.model;

/**
 * Lifecycle state of an event in the database ({@code events.status}).
 * <p>
 * Values match the Postgres enum labels used by the API and docs. Transitions
 * (for example to {@link #archived} or {@link #soft_deleted}) are enforced in
 * the service layer and by scheduled jobs, not in this enum itself.
 */
public enum EventStatus {
    active,
    flagged,
    archived,
    soft_deleted
}
