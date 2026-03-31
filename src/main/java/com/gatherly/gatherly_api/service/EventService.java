package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventListItemResponse;
import com.gatherly.gatherly_api.dto.UpdateEventRequest;
import com.gatherly.gatherly_api.dto.EventListResponse;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.dto.OrganizerEventItemResponse;
import com.gatherly.gatherly_api.dto.OrganizerEventListResponse;
import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.Category;
import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventCategory;
import com.gatherly.gatherly_api.model.FlagReason;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.repository.CategoryRepository;
import com.gatherly.gatherly_api.repository.EventCategoryRepository;
import com.gatherly.gatherly_api.repository.EventRepository;
import com.gatherly.gatherly_api.repository.ProfileRepository;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service layer for event-related business rules and database work.
 * <p>
 * In a Spring app, the <strong>controller</strong> handles HTTP (status codes, JSON),
 * while the <strong>service</strong> is where you put rules like “hybrid events need both
 * a meeting link and an address.” That keeps controllers thin and makes the same logic
 * reusable if you add another entry point later (for example a CLI or batch job).
 * <p>
 * Implements create, update, soft delete, restore, public list/detail reads, and the organizer dashboard list.
 */
@Service
public class EventService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    /** Soft-deleted events remain visible to the organizer until this many days after {@code deleted_at}. */
    private static final int SOFT_DELETE_GRACE_DAYS = 7;

    /** Looks up the organizer’s {@link Profile} row (the user id from the JWT). */
    private final ProfileRepository profileRepository;
    /** Looks up category rows when the client sends {@code categoryIds}. */
    private final CategoryRepository categoryRepository;
    /** Persists the main {@code events} row. */
    private final EventRepository eventRepository;
    /** Inserts rows into the {@code event_categories} junction table. */
    private final EventCategoryRepository eventCategoryRepository;
    /**
     * Low-level JPA handle used to {@link EntityManager#refresh(Object)} the event after insert
     * so fields managed only by Postgres (RSVP count, timestamps) show up on the entity.
     */
    private final EntityManager entityManager;

    public EventService(
            ProfileRepository profileRepository,
            CategoryRepository categoryRepository,
            EventRepository eventRepository,
            EventCategoryRepository eventCategoryRepository,
            EntityManager entityManager
    ) {
        this.profileRepository = profileRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.eventCategoryRepository = eventCategoryRepository;
        this.entityManager = entityManager;
    }

    /**
     * Creates an event owned by the given profile and links up to three categories.
     * The organizer always comes from the JWT — never from the request body.
     * <p>
     * Steps in order: validate → save event → save category links → refresh event from DB
     * → build the JSON-facing {@link EventResponse}.
     */
    @Transactional
    public EventResponse createEvent(UUID organizerId, CreateEventRequest request) {
        Profile organizer = profileRepository.findById(organizerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile not found for authenticated user."
                ));

        validateCreateRequest(request);

        List<UUID> categoryIds = request.categoryIds() == null ? List.of() : request.categoryIds();
        List<Category> categories = loadCategoriesInOrder(categoryIds);

        Event event = buildNewEvent(organizer, request);
        Event saved = eventRepository.save(event);
        // Force the INSERT to run now so the event has a real id before we attach categories.
        eventRepository.flush();

        if (!categories.isEmpty()) {
            List<EventCategory> links = new ArrayList<>();
            for (Category category : categories) {
                links.add(EventCategory.builder()
                        .event(saved)
                        .category(category)
                        .build());
            }
            eventCategoryRepository.saveAll(links);
        }

        // Re-read the row from Postgres so rsvp_count and timestamps (not sent by the app on insert)
        // match what is actually stored — the entity mapping marks those columns insertable=false.
        entityManager.refresh(saved);

        List<String> categoryNames = categories.stream().map(Category::getName).toList();
        return EventResponse.from(saved, categoryNames);
    }

    /**
     * Updates an event owned by {@code organizerId}. Immutable columns ({@code event_type}, admission, fee)
     * stay on the entity; the request only carries fields the API allows to change.
     * <p>
     * Order of operations: authorize → reject soft-deleted edits → validate body → write scalar columns →
     * replace junction rows → flush → refresh so {@code rsvp_count} and timestamps match Postgres.
     */
    @Transactional
    public EventResponse updateEvent(UUID organizerId, UUID eventId, UpdateEventRequest request) {
        Event event = loadEventForOrganizer(eventId, organizerId);
        if (event.getStatus() == EventStatus.soft_deleted) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot update a soft-deleted event. Restore it first."
            );
        }
        if (request.maxCapacity() < event.getMaxCapacity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "maxCapacity cannot be decreased after event creation."
            );
        }

        validateUpdateRequest(request, event.getEventType());

        applyUpdateToEvent(event, request);

        // Replace categories: delete old links first so we never exceed the unique constraint briefly.
        eventCategoryRepository.deleteByEvent_Id(eventId);
        eventRepository.flush();

        List<UUID> categoryIds = request.categoryIds() == null ? List.of() : request.categoryIds();
        List<Category> categories = loadCategoriesInOrder(categoryIds);
        if (!categories.isEmpty()) {
            List<EventCategory> links = new ArrayList<>();
            for (Category category : categories) {
                links.add(EventCategory.builder()
                        .event(event)
                        .category(category)
                        .build());
            }
            eventCategoryRepository.saveAll(links);
        }

        eventRepository.save(event);
        eventRepository.flush();
        entityManager.refresh(event);

        List<String> categoryNames = categories.stream().map(Category::getName).toList();
        return EventResponse.from(event, categoryNames);
    }

    /**
     * Soft-deletes an event: sets status and {@code deleted_at}. Idempotent if already deleted.
     */
    @Transactional
    public void softDeleteEvent(UUID organizerId, UUID eventId) {
        Event event = loadEventForOrganizer(eventId, organizerId);
        if (event.getStatus() == EventStatus.soft_deleted) {
            return;
        }
        event.setStatus(EventStatus.soft_deleted);
        event.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        eventRepository.save(event);
    }

    /**
     * Undoes a soft delete within the grace window. Computes {@code active} vs {@code archived} from
     * {@code end_time} vs “now” (UTC). Clears flag columns so the row’s {@link EventStatus} matches the
     * documented response (no {@code flagged} after restore).
     */
    @Transactional
    public EventResponse restoreEvent(UUID organizerId, UUID eventId) {
        Event event = loadEventForOrganizer(eventId, organizerId);
        if (event.getStatus() != EventStatus.soft_deleted) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event is not in a soft deleted state."
            );
        }
        OffsetDateTime deletedAt = event.getDeletedAt();
        if (deletedAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event is not in a soft deleted state."
            );
        }
        OffsetDateTime graceCutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(SOFT_DELETE_GRACE_DAYS);
        if (deletedAt.isBefore(graceCutoff)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
        }

        event.setDeletedAt(null);
        event.setFlagReason(null);
        event.setFlaggedBy(null);
        event.setFlaggedAt(null);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (event.getEndTime().isAfter(now)) {
            event.setStatus(EventStatus.active);
        } else {
            event.setStatus(EventStatus.archived);
        }

        eventRepository.save(event);
        eventRepository.flush();
        entityManager.refresh(event);

        List<String> categoryNames = eventCategoryRepository.findByEvent_Id(eventId).stream()
                .filter(link -> link.getCategory() != null && link.getCategory().getName() != null)
                .sorted(Comparator.comparing(EventCategory::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(link -> link.getCategory().getName())
                .toList();

        return EventResponse.from(event, categoryNames);
    }

    /**
     * Flags an event with a moderator/admin-provided reason.
     * <p>
     * Business rules:
     * <ul>
     *   <li>Only {@link Role#moderator} and {@link Role#admin} may flag.</li>
     *   <li>Flag reason is mandatory and must match the DB {@code flag_reason} enum.</li>
     *   <li>Soft-deleted events are rejected (can only be restored by the organizer).</li>
     *   <li>Already-flagged events are rejected with a 409 Conflict.</li>
     * </ul>
     */
    @Transactional
    public OrganizerEventItemResponse flagEvent(
            UUID actorId,
            Role actorRole,
            UUID eventId,
            String reason
    ) {
        if (actorRole != Role.moderator && actorRole != Role.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found."
                ));

        if (event.getStatus() == EventStatus.soft_deleted) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot flag a soft-deleted event."
            );
        }
        if (event.getStatus() == EventStatus.flagged) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Event is already flagged."
            );
        }

        FlagReason parsedReason = parseFlagReason(reason);

        Profile flaggedBy = profileRepository.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile not found for authenticated user."
                ));

        event.setStatus(EventStatus.flagged);
        event.setFlagReason(parsedReason);
        event.setFlaggedBy(flaggedBy);
        event.setFlaggedAt(OffsetDateTime.now(ZoneOffset.UTC));

        eventRepository.save(event);
        eventRepository.flush();
        entityManager.refresh(event);

        List<String> categoryNames = sortedCategoryNamesForEvent(
                eventCategoryRepository.findByEvent_Id(eventId)
        );

        return OrganizerEventItemResponse.from(event, categoryNames);
    }

    private static FlagReason parseFlagReason(String rawReason) {
        if (isBlank(rawReason)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing or invalid flag reason."
            );
        }

        String normalized = rawReason.trim().toLowerCase();
        try {
            return FlagReason.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing or invalid flag reason."
            );
        }
    }

    /**
     * Loads the row and enforces “this JWT user owns the event”: missing id → 404, wrong organizer → 403.
     */
    private Event loadEventForOrganizer(UUID eventId, UUID organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
        if (event.getOrganizer() == null || !event.getOrganizer().getId().equals(organizerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the organizer of this event.");
        }
        return event;
    }

    private static void applyUpdateToEvent(Event event, UpdateEventRequest request) {
        event.setTitle(request.title().trim());
        event.setDescription(request.description().trim());
        event.setCoverImageUrl(blankToNull(request.coverImageUrl()));
        event.setMeetingLink(blankToNull(request.meetingLink()));
        event.setAddressLine1(blankToNull(request.addressLine1()));
        event.setAddressLine2(blankToNull(request.addressLine2()));
        event.setCity(blankToNull(request.city()));
        event.setProvince(request.province());
        event.setPostalCode(blankToNull(request.postalCode()));
        event.setTimezone(request.timezone().trim());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setMaxCapacity(request.maxCapacity());
    }

    private void validateUpdateRequest(UpdateEventRequest request, EventType eventType) {
        validateTimeOrder(request.startTime(), request.endTime());
        validateCategoryIdList(request.categoryIds());
        validateEventTypeFields(
                eventType,
                request.meetingLink(),
                request.addressLine1(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        validateOptionalUrlStrings(request.meetingLink(), request.coverImageUrl());
    }

    /**
     * Returns one page of public event listings (active only).
     * <p>
     * Sorting is handled by the repository query to keep paging stable:
     * hot events first, then start time.
     */
    @Transactional(readOnly = true)
    public EventListResponse getEvents(int page, int size) {
        int normalizedPage = Math.max(page, DEFAULT_PAGE);
        int normalizedSize = normalizeSize(size);
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize);

        Page<Event> eventsPage = eventRepository.findByStatusOrderForListing(EventStatus.active, pageable);
        List<Event> events = eventsPage.getContent();
        Map<UUID, List<String>> categoriesByEventId = loadCategoryNamesByEventId(events);

        List<EventListItemResponse> content = events.stream()
                .map(event -> EventListItemResponse.from(
                        event,
                        categoriesByEventId.getOrDefault(event.getId(), List.of())
                ))
                .toList();

        return new EventListResponse(
                content,
                eventsPage.getNumber(),
                eventsPage.getSize(),
                eventsPage.getTotalElements(),
                eventsPage.getTotalPages()
        );
    }

    /**
     * Paginated dashboard for the organizer: all non-purged lifecycle states, with optional status filter.
     * Soft-deleted rows outside the grace window are excluded at query time.
     */
    @Transactional(readOnly = true)
    public OrganizerEventListResponse getMyEvents(UUID organizerId, EventStatus statusFilter, int page, int size) {
        int normalizedPage = Math.max(page, DEFAULT_PAGE);
        int normalizedSize = normalizeSize(size);
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize);
        OffsetDateTime graceCutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(SOFT_DELETE_GRACE_DAYS);

        Page<Event> eventsPage = statusFilter == null
                ? eventRepository.findOrganizerDashboardEventsAll(
                        organizerId,
                        graceCutoff,
                        EventStatus.soft_deleted,
                        pageable
                )
                : eventRepository.findOrganizerDashboardEventsFiltered(
                        organizerId,
                        graceCutoff,
                        EventStatus.soft_deleted,
                        statusFilter,
                        pageable
                );
        List<Event> events = eventsPage.getContent();
        Map<UUID, List<String>> categoriesByEventId = loadCategoryNamesByEventId(events);

        List<OrganizerEventItemResponse> content = events.stream()
                .map(event -> OrganizerEventItemResponse.from(
                        event,
                        categoriesByEventId.getOrDefault(event.getId(), List.of())
                ))
                .toList();

        return new OrganizerEventListResponse(
                content,
                eventsPage.getNumber(),
                eventsPage.getSize(),
                eventsPage.getTotalElements(),
                eventsPage.getTotalPages()
        );
    }

    /**
     * Loads one active event by id.
     * <p>
     * The caller decides whether organizer details should be included.
     */
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID eventId, boolean includeOrganizer) {
        Event event = eventRepository.findByIdAndStatus(eventId, EventStatus.active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));

        List<String> categoryNames = eventCategoryRepository.findByEvent_Id(eventId).stream()
                .filter(link -> link.getCategory() != null && link.getCategory().getName() != null)
                .sorted(Comparator.comparing(EventCategory::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(link -> link.getCategory().getName())
                .toList();

        EventResponse response = EventResponse.from(event, categoryNames);
        if (includeOrganizer) {
            return response;
        }
        return new EventResponse(
                response.id(),
                response.title(),
                response.description(),
                response.eventType(),
                response.admissionType(),
                response.admissionFee(),
                response.startTime(),
                response.endTime(),
                response.timezone(),
                response.address(),
                response.coverImageUrl(),
                response.rsvpCount(),
                response.maxCapacity(),
                response.isHot(),
                response.categories(),
                null
        );
    }

    /**
     * Maps the HTTP request DTO into a new {@link Event} entity. New events start as
     * {@link EventStatus#active} with no flag or soft-delete fields set.
     */
    private static Event buildNewEvent(Profile organizer, CreateEventRequest request) {
        BigDecimal fee = resolveAdmissionFee(request);
        return Event.builder()
                .organizer(organizer)
                .title(request.title().trim())
                .description(request.description().trim())
                .coverImageUrl(blankToNull(request.coverImageUrl()))
                .eventType(request.eventType())
                .admissionType(request.admissionType())
                .admissionFee(fee)
                .meetingLink(blankToNull(request.meetingLink()))
                .addressLine1(blankToNull(request.addressLine1()))
                .addressLine2(blankToNull(request.addressLine2()))
                .city(blankToNull(request.city()))
                .province(request.province())
                .postalCode(blankToNull(request.postalCode()))
                .timezone(request.timezone().trim())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .maxCapacity(request.maxCapacity())
                .status(EventStatus.active)
                .flaggedBy(null)
                .flagReason(null)
                .flaggedAt(null)
                .deletedAt(null)
                .build();
    }

    /**
     * Free events store {@code null} for {@code admission_fee} in the database; paid events
     * keep the amount from the request (already validated elsewhere).
     */
    private static BigDecimal resolveAdmissionFee(CreateEventRequest request) {
        if (request.admissionType() == AdmissionType.free) {
            return null;
        }
        return request.admissionFee();
    }

    /**
     * Cross-field checks that are awkward to express only with Bean Validation annotations
     * on {@link CreateEventRequest} (for example “end must be after start”).
     */
    private void validateCreateRequest(CreateEventRequest request) {
        validateTimeOrder(request.startTime(), request.endTime());
        validateCategoryIdList(request.categoryIds());
        validateAdmissionRules(request);
        validateEventTypeFields(
                request.eventType(),
                request.meetingLink(),
                request.addressLine1(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        validateOptionalUrlStrings(request.meetingLink(), request.coverImageUrl());
    }

    private static void validateTimeOrder(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endTime must be after startTime."
            );
        }
    }

    private static void validateCategoryIdList(List<UUID> categoryIds) {
        List<UUID> ids = categoryIds == null ? List.of() : categoryIds;
        if (ids.size() > 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At most three categories are allowed."
            );
        }
        if (ids.size() != new HashSet<>(ids).size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duplicate category ids are not allowed."
            );
        }
    }

    /** Ensures free vs paid rules line up with {@code admission_fee} in the schema. */
    private static void validateAdmissionRules(CreateEventRequest request) {
        if (request.admissionType() == AdmissionType.free) {
            if (request.admissionFee() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "admissionFee must be omitted for free events."
                );
            }
            return;
        }
        BigDecimal fee = request.admissionFee();
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "admissionFee is required and must be greater than zero for paid events."
            );
        }
    }

    /**
     * Virtual/hybrid need a link; in-person/hybrid need a Canadian address block.
     * These rules mirror {@code docs/api_endpoints.md} and the database check constraints.
     */
    private static void validateEventTypeFields(
            EventType type,
            String meetingLink,
            String addressLine1,
            String city,
            Province province,
            String postalCode
    ) {
        boolean needLink = type == EventType.virtual || type == EventType.hybrid;
        boolean needAddress = type == EventType.in_person || type == EventType.hybrid;

        if (needLink && isBlank(meetingLink)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "meetingLink is required for virtual and hybrid events."
            );
        }

        if (needAddress) {
            if (isBlank(addressLine1)
                    || isBlank(city)
                    || province == null
                    || isBlank(postalCode)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "addressLine1, city, province, and postalCode are required for in-person and hybrid events."
                );
            }
        }
    }

    /** When present, meeting and cover URLs must be real http(s) URLs with a host. */
    private static void validateOptionalUrlStrings(String meetingLink, String coverImageUrl) {
        if (!isBlank(meetingLink)) {
            requireHttpOrHttpsUrl("meetingLink", meetingLink.trim());
        }
        if (!isBlank(coverImageUrl)) {
            requireHttpOrHttpsUrl("coverImageUrl", coverImageUrl.trim());
        }
    }

    /** Parses the string as a {@link URI} and insists on an {@code http} or {@code https} scheme plus a host. */
    private static void requireHttpOrHttpsUrl(String field, String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw invalidUrl(field);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw invalidUrl(field);
            }
        } catch (IllegalArgumentException ex) {
            throw invalidUrl(field);
        }
    }

    private static ResponseStatusException invalidUrl(String field) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                field + " must be a valid http or https URL."
        );
    }

    /**
     * Fetches categories by id and returns them in the <strong>same order</strong> as the request,
     * so the API response lists category names in the order the client sent.
     */
    private List<Category> loadCategoriesInOrder(List<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        List<Category> found = categoryRepository.findAllById(categoryIds);
        if (found.size() != categoryIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more category ids are invalid."
            );
        }
        Map<UUID, Category> byId = found.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Category> ordered = new ArrayList<>();
        for (UUID id : categoryIds) {
            ordered.add(Objects.requireNonNull(byId.get(id)));
        }
        return ordered;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Empty or whitespace-only optional strings become {@code null} so we store clean SQL {@code NULL}s. */
    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private List<String> sortedCategoryNamesForEvent(List<EventCategory> linksForEvent) {
        return linksForEvent.stream()
                .filter(link -> link.getCategory() != null && link.getCategory().getName() != null)
                .sorted(Comparator.comparing(EventCategory::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(link -> link.getCategory().getName())
                .toList();
    }

    private Map<UUID, List<String>> loadCategoryNamesByEventId(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<UUID> eventIds = events.stream().map(Event::getId).toList();
        List<EventCategory> links = eventCategoryRepository.findByEvent_IdIn(eventIds);

        Map<UUID, List<EventCategory>> linksByEventId = links.stream()
                .filter(link -> link.getEvent() != null && link.getEvent().getId() != null)
                .collect(Collectors.groupingBy(
                        link -> link.getEvent().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<UUID, List<String>> namesByEventId = new LinkedHashMap<>();
        for (UUID eventId : eventIds) {
            List<EventCategory> linksForEvent = linksByEventId.getOrDefault(eventId, List.of());
            namesByEventId.put(eventId, sortedCategoryNamesForEvent(linksForEvent));
        }
        return namesByEventId;
    }
}
