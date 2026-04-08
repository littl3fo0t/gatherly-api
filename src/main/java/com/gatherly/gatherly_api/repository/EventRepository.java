package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link Event} rows.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Public listing: active events only, ordered hot-first then by start time.
     * <p>
     * Implemented as native SQL so PostgreSQL evaluates the hot sort reliably (the JPQL
     * variant with {@code CASE} arithmetic has been observed to fail against Postgres in production).
     */
    @Query(
            value = """
                    SELECT e.*
                    FROM events e
                    WHERE e.status = 'active'
                    ORDER BY
                      CASE
                        WHEN (COALESCE(e.rsvp_count, 0) * 100) >= (e.max_capacity * 80) THEN 1
                        ELSE 0
                      END DESC,
                      e.start_time ASC,
                      e.id ASC
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM events e
                    WHERE e.status = 'active'
                    """,
            nativeQuery = true
    )
    Page<Event> findActiveForListing(Pageable pageable);

    /**
     * Finds one event by id only when it is in the requested status.
     */
    Optional<Event> findByIdAndStatus(UUID id, EventStatus status);

    /**
     * Loads an event row while taking a write lock.
     * <p>
     * This is used for capacity-sensitive operations (RSVP create/cancel) so two requests
     * can't both read the same "last available seat" and overbook the event.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Organizer dashboard without status filter. Split from the filtered variant so Postgres never sees a
     * nullable enum parameter (avoids {@code could not determine data type of parameter} on {@code OR} clauses).
     * <p>
     * {@code softDeleted} must be {@link EventStatus#soft_deleted}.
     */
    @Query(
            value = """
                    SELECT e
                    FROM Event e
                    WHERE e.organizer.id = :organizerId
                      AND (
                        e.status <> :softDeleted
                        OR e.deletedAt IS NULL
                        OR e.deletedAt >= :graceCutoff
                      )
                    ORDER BY e.startTime DESC, e.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM Event e
                    WHERE e.organizer.id = :organizerId
                      AND (
                        e.status <> :softDeleted
                        OR e.deletedAt IS NULL
                        OR e.deletedAt >= :graceCutoff
                      )
                    """
    )
    Page<Event> findOrganizerDashboardEventsAll(
            @Param("organizerId") UUID organizerId,
            @Param("graceCutoff") OffsetDateTime graceCutoff,
            @Param("softDeleted") EventStatus softDeleted,
            Pageable pageable
    );

    /**
     * Same as {@link #findOrganizerDashboardEventsAll} but restricted to {@code statusFilter}.
     */
    @Query(
            value = """
                    SELECT e
                    FROM Event e
                    WHERE e.organizer.id = :organizerId
                      AND (
                        e.status <> :softDeleted
                        OR e.deletedAt IS NULL
                        OR e.deletedAt >= :graceCutoff
                      )
                      AND e.status = :statusFilter
                    ORDER BY e.startTime DESC, e.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM Event e
                    WHERE e.organizer.id = :organizerId
                      AND (
                        e.status <> :softDeleted
                        OR e.deletedAt IS NULL
                        OR e.deletedAt >= :graceCutoff
                      )
                      AND e.status = :statusFilter
                    """
    )
    Page<Event> findOrganizerDashboardEventsFiltered(
            @Param("organizerId") UUID organizerId,
            @Param("graceCutoff") OffsetDateTime graceCutoff,
            @Param("softDeleted") EventStatus softDeleted,
            @Param("statusFilter") EventStatus statusFilter,
            Pageable pageable
    );
}
