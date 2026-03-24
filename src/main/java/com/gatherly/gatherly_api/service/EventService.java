package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.Category;
import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventCategory;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.repository.CategoryRepository;
import com.gatherly.gatherly_api.repository.EventCategoryRepository;
import com.gatherly.gatherly_api.repository.EventRepository;
import com.gatherly.gatherly_api.repository.ProfileRepository;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Today this class only implements {@link #createEvent}; list/detail/update endpoints
 * can call into here as you add them.
 */
@Service
public class EventService {

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
        OffsetDateTime start = request.startTime();
        OffsetDateTime end = request.endTime();
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endTime must be after startTime."
            );
        }

        List<UUID> categoryIds = request.categoryIds() == null ? List.of() : request.categoryIds();
        if (categoryIds.size() > 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At most three categories are allowed."
            );
        }
        if (categoryIds.size() != new HashSet<>(categoryIds).size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duplicate category ids are not allowed."
            );
        }

        validateAdmissionRules(request);
        validateEventTypeRules(request);
        validateOptionalUrls(request);
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
    private static void validateEventTypeRules(CreateEventRequest request) {
        EventType type = request.eventType();
        boolean needLink = type == EventType.virtual || type == EventType.hybrid;
        boolean needAddress = type == EventType.in_person || type == EventType.hybrid;

        if (needLink && isBlank(request.meetingLink())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "meetingLink is required for virtual and hybrid events."
            );
        }

        if (needAddress) {
            if (isBlank(request.addressLine1())
                    || isBlank(request.city())
                    || request.province() == null
                    || isBlank(request.postalCode())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "addressLine1, city, province, and postalCode are required for in-person and hybrid events."
                );
            }
        }
    }

    /** When present, meeting and cover URLs must be real http(s) URLs with a host. */
    private static void validateOptionalUrls(CreateEventRequest request) {
        if (!isBlank(request.meetingLink())) {
            requireHttpOrHttpsUrl("meetingLink", request.meetingLink().trim());
        }
        if (!isBlank(request.coverImageUrl())) {
            requireHttpOrHttpsUrl("coverImageUrl", request.coverImageUrl().trim());
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
}
