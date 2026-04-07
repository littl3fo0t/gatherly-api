package com.gatherly.gatherly_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a row in the {@code profiles} table.
 * <p>
 * This entity stores app-owned user profile data that lives alongside
 * Supabase-authenticated identities.
 */
@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "role_enum")
    private Role role;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "province", columnDefinition = "province_enum")
    private Province province;

    @Column(name = "postal_code", length = 7)
    private String postalCode;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * Applies only the fields that users are allowed to edit through /profiles/me.
     * Email and role are intentionally excluded to enforce endpoint rules.
     */
    public void applySelfServiceUpdate(
            String fullName,
            String avatarUrl,
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String postalCode
    ) {
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.province = (province == null || province.isBlank()) ? null : Province.valueOf(province);
        this.postalCode = postalCode;
    }

    /** Updates role for admin-only flows (e.g. promote/demote moderator). */
    public void applyRole(Role newRole) {
        this.role = newRole;
    }
}
