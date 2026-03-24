package com.gatherly.gatherly_api.dto;

/**
 * A single field-level validation error in an API error response.
 */
public record ApiFieldError(String field, String message) {
}
