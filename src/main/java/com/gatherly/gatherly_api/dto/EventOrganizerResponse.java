package com.gatherly.gatherly_api.dto;

import java.util.UUID;

/**
 * Minimal organizer block returned on event detail and create responses.
 */
public record EventOrganizerResponse(UUID id, String fullName) {
}
