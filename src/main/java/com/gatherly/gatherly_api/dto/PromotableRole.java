package com.gatherly.gatherly_api.dto;

/**
 * Roles that can be assigned via {@code PATCH /api/admin/users/{id}/role} (user or moderator only).
 */
public enum PromotableRole {
    user,
    moderator
}
