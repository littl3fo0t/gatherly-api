package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.model.EventCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence access for {@link EventCategory} junction rows.
 */
public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    /**
     * Loads all category links for an event, used when building API responses.
     */
    List<EventCategory> findByEvent_Id(UUID eventId);
}
