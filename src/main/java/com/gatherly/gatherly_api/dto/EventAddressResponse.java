package com.gatherly.gatherly_api.dto;

/**
 * Physical location fields for an event, grouped for JSON as {@code "address": { ... }}.
 */
public record EventAddressResponse(
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode
) {
}
