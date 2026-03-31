package com.gatherly.gatherly_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a row in the {@code rsvps} table.
 * <p>
 * This entity is intentionally simple: it models how data is stored in Postgres.
 * Business rules (capacity, time windows, who can RSVP/cancel) belong in the service layer.
 */
@Entity
@Table(name = "rsvps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Which event this RSVP is for (foreign key {@code event_id} → {@code events.id}).
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    /**
     * Who RSVPed (foreign key {@code user_id} → {@code profiles.id}).
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private Profile user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "rsvp_status_enum")
    private RsvpStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}

