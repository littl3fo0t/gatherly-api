package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.dto.RsvpWithEventSummaryRow;
import com.gatherly.gatherly_api.model.Rsvp;
import com.gatherly.gatherly_api.model.RsvpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link Rsvp} rows.
 */
public interface RsvpRepository extends JpaRepository<Rsvp, UUID> {

    Optional<Rsvp> findByEvent_IdAndUser_Id(UUID eventId, UUID userId);

    /**
     * Upcoming RSVPs for one user, without a status filter.
     * <p>
     * This method returns a DTO projection rather than entities so pagination stays in SQL
     * (no fetch-join + in-memory paging surprises).
     */
    @Query(value = """
            SELECT new com.gatherly.gatherly_api.dto.RsvpWithEventSummaryRow(
              r.id,
              r.status,
              r.createdAt,
              r.updatedAt,
              e.id,
              e.title,
              e.eventType,
              e.admissionType,
              e.admissionFee,
              e.startTime,
              e.endTime,
              e.timezone,
              e.city,
              e.province,
              e.coverImageUrl
            )
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND e.startTime > :now
            ORDER BY e.startTime ASC, r.id ASC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND e.startTime > :now
            """
    )
    Page<RsvpWithEventSummaryRow> findMyUpcomingAll(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    /**
     * Upcoming RSVPs for one user, restricted to a single status.
     */
    @Query(value = """
            SELECT new com.gatherly.gatherly_api.dto.RsvpWithEventSummaryRow(
              r.id,
              r.status,
              r.createdAt,
              r.updatedAt,
              e.id,
              e.title,
              e.eventType,
              e.admissionType,
              e.admissionFee,
              e.startTime,
              e.endTime,
              e.timezone,
              e.city,
              e.province,
              e.coverImageUrl
            )
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND r.status = :status
              AND e.startTime > :now
            ORDER BY e.startTime ASC, r.id ASC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND r.status = :status
              AND e.startTime > :now
            """
    )
    Page<RsvpWithEventSummaryRow> findMyUpcomingFiltered(
            @Param("userId") UUID userId,
            @Param("status") RsvpStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    /**
     * Past RSVPs for one user, without a status filter.
     */
    @Query(value = """
            SELECT new com.gatherly.gatherly_api.dto.RsvpWithEventSummaryRow(
              r.id,
              r.status,
              r.createdAt,
              r.updatedAt,
              e.id,
              e.title,
              e.eventType,
              e.admissionType,
              e.admissionFee,
              e.startTime,
              e.endTime,
              e.timezone,
              e.city,
              e.province,
              e.coverImageUrl
            )
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND e.startTime <= :now
            ORDER BY e.startTime DESC, r.id DESC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND e.startTime <= :now
            """
    )
    Page<RsvpWithEventSummaryRow> findMyPastAll(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    /**
     * Past RSVPs for one user, restricted to a single status.
     */
    @Query(value = """
            SELECT new com.gatherly.gatherly_api.dto.RsvpWithEventSummaryRow(
              r.id,
              r.status,
              r.createdAt,
              r.updatedAt,
              e.id,
              e.title,
              e.eventType,
              e.admissionType,
              e.admissionFee,
              e.startTime,
              e.endTime,
              e.timezone,
              e.city,
              e.province,
              e.coverImageUrl
            )
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND r.status = :status
              AND e.startTime <= :now
            ORDER BY e.startTime DESC, r.id DESC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Rsvp r
            JOIN r.event e
            WHERE r.user.id = :userId
              AND r.status = :status
              AND e.startTime <= :now
            """
    )
    Page<RsvpWithEventSummaryRow> findMyPastFiltered(
            @Param("userId") UUID userId,
            @Param("status") RsvpStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );
}

