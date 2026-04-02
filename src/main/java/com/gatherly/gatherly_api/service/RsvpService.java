package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.MyRsvpsResponse;
import com.gatherly.gatherly_api.dto.PageResponse;
import com.gatherly.gatherly_api.dto.RsvpResponse;
import com.gatherly.gatherly_api.dto.RsvpWithEventSummary;
import com.gatherly.gatherly_api.dto.RsvpWithEventSummaryRow;
import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Rsvp;
import com.gatherly.gatherly_api.model.RsvpStatus;
import com.gatherly.gatherly_api.repository.EventRepository;
import com.gatherly.gatherly_api.repository.ProfileRepository;
import com.gatherly.gatherly_api.repository.RsvpRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Service layer for RSVP-related business rules and database work.
 * <p>
 * Controllers should stay thin (HTTP + JSON). This class is where we enforce rules like:
 * - "you can't RSVP after the start time"
 * - "you can't RSVP if the event is full"
 * - "you can't RSVP twice (even if you cancelled)"
 */
@Service
public class RsvpService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProfileRepository profileRepository;
    private final EventRepository eventRepository;
    private final RsvpRepository rsvpRepository;
    private final EntityManager entityManager;

    public RsvpService(
            ProfileRepository profileRepository,
            EventRepository eventRepository,
            RsvpRepository rsvpRepository,
            EntityManager entityManager
    ) {
        this.profileRepository = profileRepository;
        this.eventRepository = eventRepository;
        this.rsvpRepository = rsvpRepository;
        this.entityManager = entityManager;
    }

    /**
     * Creates a confirmed RSVP for one user on one active event.
     * <p>
     * We lock the event row so two requests can't both observe the same capacity and overbook.
     */
    @Transactional
    public RsvpResponse createRsvp(UUID userId, UUID eventId) {
        Profile user = profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile not found for authenticated user."
                ));

        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found."
                ));

        requireActiveEvent(event);
        requireAdmissionsOpen(event);
        requireCapacityAvailable(event);

        // UNIQUE(event_id, user_id) also enforces this, but we check early so we can return 409
        // instead of bubbling a database constraint error up as a 500.
        if (rsvpRepository.findByEvent_IdAndUser_Id(eventId, userId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User has already RSVPed for this event."
            );
        }

        Rsvp rsvp = Rsvp.builder()
                .event(event)
                .user(user)
                .status(RsvpStatus.confirmed)
                .build();

        Rsvp saved = rsvpRepository.save(rsvp);
        // created_at / updated_at are set by Postgres defaults + triggers, not by the application.
        // Refresh so the API response includes those DB-managed values.
        // NOTE: refresh requires the row to exist in the DB, so we must flush before refreshing.
        entityManager.flush();
        entityManager.refresh(saved);
        return RsvpResponse.from(saved);
    }

    /**
     * Cancels the user's RSVP before the event start time.
     * <p>
     * We update status to {@code cancelled} instead of deleting the row to preserve history,
     * and because the DB unique constraint prevents re-RSVPing after cancellation.
     */
    @Transactional
    public RsvpResponse cancelRsvp(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found."
                ));

        requireActiveEvent(event);
        requireAdmissionsOpen(event);

        Rsvp rsvp = rsvpRepository.findByEvent_IdAndUser_Id(eventId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No active RSVP found for this user and event."
                ));

        if (rsvp.getStatus() != RsvpStatus.confirmed) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No active RSVP found for this user and event."
            );
        }

        rsvp.setStatus(RsvpStatus.cancelled);
        Rsvp saved = rsvpRepository.save(rsvp);
        // updated_at is DB-managed; refresh so the response shows the persisted timestamp.
        entityManager.flush();
        entityManager.refresh(saved);
        return RsvpResponse.from(saved);
    }

    /**
     * Returns the authenticated user's RSVPs split into upcoming vs past.
     * <p>
     * Upcoming/past is defined by comparing the event start time to \"now\" (UTC).
     */
    @Transactional(readOnly = true)
    public MyRsvpsResponse getMyRsvps(UUID userId, String statusFilter, int page, int size) {
        int normalizedPage = Math.max(page, DEFAULT_PAGE);
        int normalizedSize = normalizeSize(size);
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RsvpStatus status = parseOptionalStatusFilter(statusFilter);

        org.springframework.data.domain.Page<RsvpWithEventSummaryRow> upcomingRows = status == null
                ? rsvpRepository.findMyUpcomingAll(userId, now, pageable)
                : rsvpRepository.findMyUpcomingFiltered(userId, status, now, pageable);
        org.springframework.data.domain.Page<RsvpWithEventSummaryRow> pastRows = status == null
                ? rsvpRepository.findMyPastAll(userId, now, pageable)
                : rsvpRepository.findMyPastFiltered(userId, status, now, pageable);

        org.springframework.data.domain.Page<RsvpWithEventSummary> upcomingPage = upcomingRows.map(RsvpWithEventSummary::fromRow);
        org.springframework.data.domain.Page<RsvpWithEventSummary> pastPage = pastRows.map(RsvpWithEventSummary::fromRow);

        PageResponse<RsvpWithEventSummary> upcoming = PageResponse.from(upcomingPage);
        PageResponse<RsvpWithEventSummary> past = PageResponse.from(pastPage);

        return new MyRsvpsResponse(upcoming, past);
    }

    private static void requireActiveEvent(Event event) {
        if (event.getStatus() != EventStatus.active) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
        }
    }

    private static void requireAdmissionsOpen(Event event) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (event.getStartTime() == null || !event.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event start time has already passed — admissions closed."
            );
        }
    }

    private static void requireCapacityAvailable(Event event) {
        int count = event.getRsvpCount() == null ? 0 : event.getRsvpCount();
        int cap = event.getMaxCapacity() == null ? 0 : event.getMaxCapacity();
        if (cap > 0 && count >= cap) {
            // Spring 7 deprecated HttpStatus.UNPROCESSABLE_ENTITY in favor of HttpStatusCode.
            // We keep the numeric code so clients still receive 422 as documented.
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatusCode.valueOf(422),
                    "Event is at maximum capacity."
            );
        }
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Optional {@code status} for GET /rsvps/my; must match {@link RsvpStatus} enum labels.
     */
    private static RsvpStatus parseOptionalStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return RsvpStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter.");
        }
    }
}

