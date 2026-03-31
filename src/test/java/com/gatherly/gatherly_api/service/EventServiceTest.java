package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.CreateEventRequest;
import com.gatherly.gatherly_api.dto.EventListResponse;
import com.gatherly.gatherly_api.dto.EventResponse;
import com.gatherly.gatherly_api.dto.UpdateEventRequest;
import com.gatherly.gatherly_api.dto.OrganizerEventListResponse;
import com.gatherly.gatherly_api.dto.OrganizerEventItemResponse;
import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.Category;
import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventCategory;
import com.gatherly.gatherly_api.model.FlagReason;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.repository.CategoryRepository;
import com.gatherly.gatherly_api.repository.EventCategoryRepository;
import com.gatherly.gatherly_api.repository.EventRepository;
import com.gatherly.gatherly_api.repository.ProfileRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final UUID ORGANIZER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MODERATOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SAVED_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventCategoryRepository eventCategoryRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private EventService eventService;

    private Profile organizer;

    @BeforeEach
    void setUp() {
        organizer = Profile.builder()
                .id(ORGANIZER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.user)
                .avatarUrl(null)
                .addressLine1(null)
                .addressLine2(null)
                .city(null)
                .province(null)
                .postalCode(null)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Test
    void createEvent_happyPath_savesEventAndCategories() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        Category category = Category.builder()
                .id(CATEGORY_ID)
                .name("Meetup")
                .slug("meetup")
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();
        when(categoryRepository.findAllById(List.of(CATEGORY_ID))).thenReturn(List.of(category));

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event draft = invocation.getArgument(0);
            return persistedEvent(draft, SAVED_EVENT_ID);
        });
        doNothing().when(entityManager).refresh(any(Event.class));

        CreateEventRequest request = validInPersonRequest(List.of(CATEGORY_ID));

        EventResponse response = eventService.createEvent(ORGANIZER_ID, request);

        assertEquals(SAVED_EVENT_ID, response.id());
        assertEquals("Spring Meetup", response.title());
        assertEquals(List.of("Meetup"), response.categories());
        assertEquals(ORGANIZER_ID, response.organizer().id());
        assertEquals("Jane Doe", response.organizer().fullName());

        verify(eventRepository).save(any(Event.class));
        verify(eventCategoryRepository).saveAll(anyList());
    }

    @Test
    void createEvent_whenProfileMissing_throws404() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.empty());

        CreateEventRequest request = validInPersonRequest(List.of());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(NOT_FOUND, ex.getStatusCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_whenEndNotAfterStart_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        OffsetDateTime t = OffsetDateTime.parse("2026-04-01T18:00:00Z");
        CreateEventRequest request = new CreateEventRequest(
                "T",
                "D",
                EventType.in_person,
                AdmissionType.free,
                t,
                t,
                "America/Toronto",
                "1 St",
                null,
                "Toronto",
                Province.ON,
                "M5V 1A1",
                null,
                null,
                null,
                10,
                List.of()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_virtualWithoutMeetingLink_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        CreateEventRequest request = new CreateEventRequest(
                "T",
                "D",
                EventType.virtual,
                AdmissionType.free,
                OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                "America/Toronto",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                List.of()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_freeWithAdmissionFee_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        CreateEventRequest request = validInPersonRequest(List.of());
        CreateEventRequest bad = new CreateEventRequest(
                request.title(),
                request.description(),
                request.eventType(),
                AdmissionType.free,
                request.startTime(),
                request.endTime(),
                request.timezone(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.province(),
                request.postalCode(),
                request.meetingLink(),
                request.coverImageUrl(),
                BigDecimal.ONE,
                request.maxCapacity(),
                request.categoryIds()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, bad)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_paidWithoutFee_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        CreateEventRequest request = validInPersonRequest(List.of());
        CreateEventRequest bad = new CreateEventRequest(
                request.title(),
                request.description(),
                request.eventType(),
                AdmissionType.paid,
                request.startTime(),
                request.endTime(),
                request.timezone(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.province(),
                request.postalCode(),
                request.meetingLink(),
                request.coverImageUrl(),
                null,
                request.maxCapacity(),
                request.categoryIds()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, bad)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_moreThanThreeCategories_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        List<UUID> four = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004")
        );
        CreateEventRequest request = validInPersonRequest(four);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_duplicateCategoryIds_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        List<UUID> dup = List.of(CATEGORY_ID, CATEGORY_ID);
        CreateEventRequest request = validInPersonRequest(dup);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_unknownCategoryId_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(categoryRepository.findAllById(List.of(CATEGORY_ID))).thenReturn(List.of());

        CreateEventRequest request = validInPersonRequest(List.of(CATEGORY_ID));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_inPersonMissingAddress_throws400() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        CreateEventRequest request = new CreateEventRequest(
                "T",
                "D",
                EventType.in_person,
                AdmissionType.free,
                OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                "America/Toronto",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                List.of()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(ORGANIZER_ID, request)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createEvent_savesEventWithActiveStatus() {
        when(profileRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event draft = invocation.getArgument(0);
            return persistedEvent(draft, SAVED_EVENT_ID);
        });
        doNothing().when(entityManager).refresh(any(Event.class));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);

        eventService.createEvent(ORGANIZER_ID, validInPersonRequest(List.of()));

        verify(eventRepository).save(captor.capture());
        assertEquals(EventStatus.active, captor.getValue().getStatus());
    }

    @Test
    void getEvents_returnsMappedPageAndFiltersActiveInRepositoryCall() {
        Event first = persistedEvent(eventDraft("Hot Event", 80, 100), SAVED_EVENT_ID);
        Event second = persistedEvent(
                eventDraft("Warm Event", 10, 100),
                UUID.fromString("00000000-0000-0000-0000-0000000000bc")
        );
        when(eventRepository.findByStatusOrderForListing(
                org.mockito.ArgumentMatchers.eq(EventStatus.active),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 25), 2));

        Category meetup = Category.builder()
                .id(CATEGORY_ID)
                .name("Meetup")
                .slug("meetup")
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();

        EventCategory firstCategory = EventCategory.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000031"))
                .event(first)
                .category(meetup)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();

        when(eventCategoryRepository.findByEvent_IdIn(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(firstCategory));

        EventListResponse response = eventService.getEvents(0, 25);

        assertEquals(2, response.content().size());
        assertEquals("Hot Event", response.content().get(0).title());
        assertEquals(true, response.content().get(0).isHot());
        assertEquals(List.of("Meetup"), response.content().get(0).categories());
        assertEquals(List.of(), response.content().get(1).categories());
        assertEquals(2, response.totalElements());
    }

    @Test
    void getEvents_emptyPage_returnsValidEnvelope() {
        when(eventRepository.findByStatusOrderForListing(
                org.mockito.ArgumentMatchers.eq(EventStatus.active),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        EventListResponse response = eventService.getEvents(0, 25);

        assertEquals(0, response.content().size());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        verify(eventCategoryRepository, never()).findByEvent_IdIn(anyList());
    }

    @Test
    void getMyEvents_returnsMappedPageAndCallsRepository() {
        Event e = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findOrganizerDashboardEventsAll(
                eq(ORGANIZER_ID),
                any(OffsetDateTime.class),
                eq(EventStatus.soft_deleted),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(e), PageRequest.of(0, 25), 1));
        when(eventCategoryRepository.findByEvent_IdIn(List.of(e.getId()))).thenReturn(List.of());

        OrganizerEventListResponse response = eventService.getMyEvents(ORGANIZER_ID, null, 0, 25);

        assertEquals(1, response.content().size());
        assertEquals("Mine", response.content().get(0).title());
        assertEquals("active", response.content().get(0).status());
        assertEquals(ORGANIZER_ID, response.content().get(0).organizer().id());
        assertEquals(1, response.totalElements());
    }

    @Test
    void getMyEvents_passesStatusFilterToRepository() {
        when(eventRepository.findOrganizerDashboardEventsFiltered(
                eq(ORGANIZER_ID),
                any(OffsetDateTime.class),
                eq(EventStatus.soft_deleted),
                eq(EventStatus.flagged),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        OrganizerEventListResponse response = eventService.getMyEvents(ORGANIZER_ID, EventStatus.flagged, 0, 25);

        assertEquals(0, response.content().size());
        verify(eventRepository).findOrganizerDashboardEventsFiltered(
                eq(ORGANIZER_ID),
                any(OffsetDateTime.class),
                eq(EventStatus.soft_deleted),
                eq(EventStatus.flagged),
                any(Pageable.class)
        );
    }

    @Test
    void getMyEvents_usesGraceCutoffSevenDaysAgoUtc() {
        when(eventRepository.findOrganizerDashboardEventsAll(
                eq(ORGANIZER_ID),
                any(OffsetDateTime.class),
                eq(EventStatus.soft_deleted),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        eventService.getMyEvents(ORGANIZER_ID, null, 0, 25);

        ArgumentCaptor<OffsetDateTime> graceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(eventRepository).findOrganizerDashboardEventsAll(
                eq(ORGANIZER_ID),
                graceCaptor.capture(),
                eq(EventStatus.soft_deleted),
                any(Pageable.class)
        );
        OffsetDateTime expected = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        long deltaSeconds = Math.abs(ChronoUnit.SECONDS.between(graceCaptor.getValue(), expected));
        assertTrue(deltaSeconds <= 3L);
    }

    @Test
    void getEventById_includeOrganizerTrue_returnsOrganizer() {
        Event event = persistedEvent(eventDraft("Detail Event", 20, 100), SAVED_EVENT_ID);
        event = persistedEventWithOrganizer(event, organizer);
        when(eventRepository.findByIdAndStatus(SAVED_EVENT_ID, EventStatus.active)).thenReturn(Optional.of(event));

        Category category = Category.builder()
                .id(CATEGORY_ID)
                .name("Meetup")
                .slug("meetup")
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();
        EventCategory link = EventCategory.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000091"))
                .event(event)
                .category(category)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();
        when(eventCategoryRepository.findByEvent_Id(SAVED_EVENT_ID)).thenReturn(List.of(link));

        EventResponse response = eventService.getEventById(SAVED_EVENT_ID, true);

        assertEquals(SAVED_EVENT_ID, response.id());
        assertEquals("Detail Event", response.title());
        assertEquals("Meetup", response.categories().get(0));
        assertEquals(ORGANIZER_ID, response.organizer().id());
    }

    @Test
    void getEventById_includeOrganizerFalse_omitsOrganizer() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Detail Event", 20, 100), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findByIdAndStatus(SAVED_EVENT_ID, EventStatus.active)).thenReturn(Optional.of(event));
        when(eventCategoryRepository.findByEvent_Id(SAVED_EVENT_ID)).thenReturn(List.of());

        EventResponse response = eventService.getEventById(SAVED_EVENT_ID, false);

        assertEquals(SAVED_EVENT_ID, response.id());
        assertEquals(null, response.organizer());
    }

    @Test
    void getEventById_missing_throws404() {
        when(eventRepository.findByIdAndStatus(SAVED_EVENT_ID, EventStatus.active)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.getEventById(SAVED_EVENT_ID, false)
        );
        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateEvent_whenEventMissing_throws404() {
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(ORGANIZER_ID, SAVED_EVENT_ID, validUpdateRequest())
        );
        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateEvent_whenNotOrganizer_throws403() {
        UUID otherId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Profile otherOrganizer = Profile.builder()
                .id(otherId)
                .fullName("Other")
                .email("other@example.com")
                .role(Role.user)
                .avatarUrl(null)
                .addressLine1(null)
                .addressLine2(null)
                .city(null)
                .province(null)
                .postalCode(null)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                otherOrganizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(ORGANIZER_ID, SAVED_EVENT_ID, validUpdateRequest())
        );
        assertEquals(FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateEvent_whenSoftDeleted_throws400() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        event.setStatus(EventStatus.soft_deleted);
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(ORGANIZER_ID, SAVED_EVENT_ID, validUpdateRequest())
        );
        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateEvent_whenMaxCapacityDecreases_throws409() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        UpdateEventRequest req = new UpdateEventRequest(
                "T",
                "<p>D</p>",
                OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                "America/Toronto",
                "123 Main St",
                null,
                "Toronto",
                Province.ON,
                "M5V 1A1",
                null,
                null,
                40,
                List.of()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(ORGANIZER_ID, SAVED_EVENT_ID, req)
        );
        assertEquals(CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateEvent_happyPath_replacesCategoriesAndRefreshes() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));
        doNothing().when(eventCategoryRepository).deleteByEvent_Id(SAVED_EVENT_ID);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entityManager).refresh(any(Event.class));

        EventResponse response = eventService.updateEvent(ORGANIZER_ID, SAVED_EVENT_ID, validUpdateRequest());

        assertEquals("Updated Title", response.title());
        verify(eventCategoryRepository).deleteByEvent_Id(SAVED_EVENT_ID);
        verify(entityManager).refresh(any(Event.class));
    }

    @Test
    void softDeleteEvent_setsStatusAndDeletedAt() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        eventService.softDeleteEvent(ORGANIZER_ID, SAVED_EVENT_ID);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertEquals(EventStatus.soft_deleted, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void restoreEvent_whenNotSoftDeleted_throws400() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.restoreEvent(ORGANIZER_ID, SAVED_EVENT_ID)
        );
        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void restoreEvent_whenGraceExpired_throws404() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        event.setStatus(EventStatus.soft_deleted);
        event.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.restoreEvent(ORGANIZER_ID, SAVED_EVENT_ID)
        );
        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void restoreEvent_whenEndTimeInFuture_setsActive() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        event.setStatus(EventStatus.soft_deleted);
        event.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        event.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        event.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entityManager).refresh(any(Event.class));
        when(eventCategoryRepository.findByEvent_Id(SAVED_EVENT_ID)).thenReturn(List.of());

        eventService.restoreEvent(ORGANIZER_ID, SAVED_EVENT_ID);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertEquals(EventStatus.active, captor.getValue().getStatus());
        assertNull(captor.getValue().getDeletedAt());
    }

    @Test
    void restoreEvent_whenEndTimeInPast_setsArchived() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        event.setStatus(EventStatus.soft_deleted);
        event.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        event.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10));
        event.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entityManager).refresh(any(Event.class));
        when(eventCategoryRepository.findByEvent_Id(SAVED_EVENT_ID)).thenReturn(List.of());

        eventService.restoreEvent(ORGANIZER_ID, SAVED_EVENT_ID);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertEquals(EventStatus.archived, captor.getValue().getStatus());
    }

    @Test
    void flagEvent_whenActorRoleNotModeratorOrAdmin_throws403() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.flagEvent(ORGANIZER_ID, Role.user, SAVED_EVENT_ID, "off_topic")
        );
        assertEquals(FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void flagEvent_whenEventMissing_throws404() {
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.flagEvent(MODERATOR_ID, Role.moderator, SAVED_EVENT_ID, "off_topic")
        );

        assertEquals(NOT_FOUND, ex.getStatusCode());
        assertEquals("Event not found.", ex.getReason());
    }

    @Test
    void flagEvent_whenSoftDeleted_throws400() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        event.setStatus(EventStatus.soft_deleted);
        event.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.flagEvent(MODERATOR_ID, Role.moderator, SAVED_EVENT_ID, "off_topic")
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot flag a soft-deleted event.", ex.getReason());
    }

    @Test
    void flagEvent_whenAlreadyFlagged_throws409() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        event.setStatus(EventStatus.flagged);

        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.flagEvent(MODERATOR_ID, Role.moderator, SAVED_EVENT_ID, "off_topic")
        );

        assertEquals(CONFLICT, ex.getStatusCode());
        assertEquals("Event is already flagged.", ex.getReason());
    }

    @Test
    void flagEvent_whenReasonMissing_throws400() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.flagEvent(MODERATOR_ID, Role.moderator, SAVED_EVENT_ID, null)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("Missing or invalid flag reason.", ex.getReason());
    }

    @Test
    void flagEvent_whenReasonInvalid_throws400() {
        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );
        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> eventService.flagEvent(MODERATOR_ID, Role.moderator, SAVED_EVENT_ID, "not_a_reason")
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("Missing or invalid flag reason.", ex.getReason());
    }

    @Test
    void flagEvent_happyPath_setsFieldsAndReturnsResponse() {
        Profile moderator = Profile.builder()
                .id(MODERATOR_ID)
                .fullName("Mod")
                .email("mod@example.com")
                .role(Role.moderator)
                .avatarUrl(null)
                .addressLine1(null)
                .addressLine2(null)
                .city(null)
                .province(null)
                .postalCode(null)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();

        Event event = persistedEventWithOrganizer(
                persistedEvent(eventDraft("Mine", 1, 50), SAVED_EVENT_ID),
                organizer
        );

        when(eventRepository.findById(SAVED_EVENT_ID)).thenReturn(Optional.of(event));
        when(profileRepository.findById(MODERATOR_ID)).thenReturn(Optional.of(moderator));
        when(eventCategoryRepository.findByEvent_Id(SAVED_EVENT_ID)).thenReturn(List.of());

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entityManager).refresh(any(Event.class));

        OrganizerEventItemResponse response = eventService.flagEvent(MODERATOR_ID, Role.moderator, SAVED_EVENT_ID, "off_topic");

        assertEquals("flagged", response.status());
        assertEquals("off_topic", response.flagReason());
        assertNotNull(response.flaggedAt());
        assertNotNull(response.flaggedBy());
        assertEquals(MODERATOR_ID, response.flaggedBy().id());
        assertEquals(ORGANIZER_ID, response.organizer().id());

        assertEquals(EventStatus.flagged, event.getStatus());
        assertEquals(FlagReason.off_topic, event.getFlagReason());
        assertEquals(MODERATOR_ID, event.getFlaggedBy().getId());
    }

    private static CreateEventRequest validInPersonRequest(List<UUID> categoryIds) {
        return new CreateEventRequest(
                "Spring Meetup",
                "<p>Hi</p>",
                EventType.in_person,
                AdmissionType.free,
                OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                "America/Toronto",
                "123 Main St",
                null,
                "Toronto",
                Province.ON,
                "M5V 1A1",
                null,
                null,
                null,
                50,
                categoryIds
        );
    }

    private static UpdateEventRequest validUpdateRequest() {
        return new UpdateEventRequest(
                "Updated Title",
                "<p>D</p>",
                OffsetDateTime.parse("2026-04-01T18:00:00Z"),
                OffsetDateTime.parse("2026-04-01T21:00:00Z"),
                "America/Toronto",
                "123 Main St",
                null,
                "Toronto",
                Province.ON,
                "M5V 1A1",
                null,
                null,
                50,
                List.of()
        );
    }

    /**
     * Simulates what Postgres + Hibernate return after insert: primary key and RSVP count filled in.
     */
    private static Event persistedEvent(Event draft, UUID id) {
        return Event.builder()
                .id(id)
                .organizer(draft.getOrganizer())
                .title(draft.getTitle())
                .description(draft.getDescription())
                .coverImageUrl(draft.getCoverImageUrl())
                .eventType(draft.getEventType())
                .admissionType(draft.getAdmissionType())
                .admissionFee(draft.getAdmissionFee())
                .meetingLink(draft.getMeetingLink())
                .addressLine1(draft.getAddressLine1())
                .addressLine2(draft.getAddressLine2())
                .city(draft.getCity())
                .province(draft.getProvince())
                .postalCode(draft.getPostalCode())
                .timezone(draft.getTimezone())
                .startTime(draft.getStartTime())
                .endTime(draft.getEndTime())
                .maxCapacity(draft.getMaxCapacity())
                .rsvpCount(draft.getRsvpCount())
                .status(draft.getStatus())
                .flagReason(draft.getFlagReason())
                .flaggedBy(draft.getFlaggedBy())
                .flaggedAt(draft.getFlaggedAt())
                .deletedAt(draft.getDeletedAt())
                .createdAt(OffsetDateTime.parse("2026-01-01T12:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-01-01T12:00:00Z"))
                .build();
    }

    private static Event eventDraft(String title, int rsvpCount, int maxCapacity) {
        return Event.builder()
                .organizer(Profile.builder().id(ORGANIZER_ID).fullName("Jane Doe").build())
                .title(title)
                .description("<p>D</p>")
                .coverImageUrl(null)
                .eventType(EventType.in_person)
                .admissionType(AdmissionType.free)
                .admissionFee(null)
                .meetingLink(null)
                .addressLine1("123 Main St")
                .addressLine2(null)
                .city("Toronto")
                .province(Province.ON)
                .postalCode("M5V 1A1")
                .timezone("America/Toronto")
                .startTime(OffsetDateTime.parse("2026-04-01T18:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-04-01T21:00:00Z"))
                .maxCapacity(maxCapacity)
                .rsvpCount(rsvpCount)
                .status(EventStatus.active)
                .build();
    }

    private static Event persistedEventWithOrganizer(Event event, Profile organizerProfile) {
        return Event.builder()
                .id(event.getId())
                .organizer(organizerProfile)
                .title(event.getTitle())
                .description(event.getDescription())
                .coverImageUrl(event.getCoverImageUrl())
                .eventType(event.getEventType())
                .admissionType(event.getAdmissionType())
                .admissionFee(event.getAdmissionFee())
                .meetingLink(event.getMeetingLink())
                .addressLine1(event.getAddressLine1())
                .addressLine2(event.getAddressLine2())
                .city(event.getCity())
                .province(event.getProvince())
                .postalCode(event.getPostalCode())
                .timezone(event.getTimezone())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .maxCapacity(event.getMaxCapacity())
                .rsvpCount(event.getRsvpCount())
                .status(event.getStatus())
                .flagReason(event.getFlagReason())
                .flaggedBy(event.getFlaggedBy())
                .flaggedAt(event.getFlaggedAt())
                .deletedAt(event.getDeletedAt())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
