package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * Persistence access for {@link Event} rows.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Returns one page of events in a deterministic order:
     * hot events first (>=80% full), then earlier start time, then id.
     */
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.status = :status
            ORDER BY
              CASE
                WHEN (COALESCE(e.rsvpCount, 0) * 100) >= (e.maxCapacity * 80) THEN 1
                ELSE 0
              END DESC,
              e.startTime ASC,
              e.id ASC
            """)
    Page<Event> findByStatusOrderForListing(EventStatus status, Pageable pageable);
}
