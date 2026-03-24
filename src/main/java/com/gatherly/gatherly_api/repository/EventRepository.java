package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.model.Event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence access for {@link Event} rows.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {
}
