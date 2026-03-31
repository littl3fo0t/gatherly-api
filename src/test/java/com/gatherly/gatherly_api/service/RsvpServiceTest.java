package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.MyRsvpsResponse;
import com.gatherly.gatherly_api.dto.RsvpResponse;
import com.gatherly.gatherly_api.model.AdmissionType;
import com.gatherly.gatherly_api.model.Event;
import com.gatherly.gatherly_api.model.EventStatus;
import com.gatherly.gatherly_api.model.EventType;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.model.Rsvp;
import com.gatherly.gatherly_api.model.RsvpStatus;
import com.gatherly.gatherly_api.repository.EventRepository;
import com.gatherly.gatherly_api.repository.ProfileRepository;
import com.gatherly.gatherly_api.repository.RsvpRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class RsvpServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID RSVP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RsvpRepository rsvpRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RsvpService rsvpService;

    @Test
    void createRsvp_duplicate_returns409() {
        Profile user = Profile.builder()
                .id(USER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.user)
                .build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Event event = Event.builder()
                .id(EVENT_ID)
                .status(EventStatus.active)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .rsvpCount(0)
                .maxCapacity(10)
                .build();
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        when(rsvpRepository.findByEvent_IdAndUser_Id(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(Rsvp.builder().id(RSVP_ID).build()));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> rsvpService.createRsvp(USER_ID, EVENT_ID)
        );
        assertEquals(CONFLICT, ex.getStatusCode());
        assertEquals("User has already RSVPed for this event.", ex.getReason());
    }

    @Test
    void createRsvp_admissionsClosed_returns400() {
        Profile user = Profile.builder()
                .id(USER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.user)
                .build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Event event = Event.builder()
                .id(EVENT_ID)
                .status(EventStatus.active)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .rsvpCount(0)
                .maxCapacity(10)
                .build();
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> rsvpService.createRsvp(USER_ID, EVENT_ID)
        );
        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("Event start time has already passed — admissions closed.", ex.getReason());
    }

    @Test
    void createRsvp_atCapacity_returns422() {
        Profile user = Profile.builder()
                .id(USER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.user)
                .build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Event event = Event.builder()
                .id(EVENT_ID)
                .status(EventStatus.active)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .rsvpCount(10)
                .maxCapacity(10)
                .build();
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> rsvpService.createRsvp(USER_ID, EVENT_ID)
        );
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("Event is at maximum capacity.", ex.getReason());
    }

    @Test
    void cancelRsvp_notConfirmed_returns404() {
        Event event = Event.builder()
                .id(EVENT_ID)
                .status(EventStatus.active)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .build();
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        Rsvp rsvp = Rsvp.builder()
                .id(RSVP_ID)
                .status(RsvpStatus.cancelled)
                .build();
        when(rsvpRepository.findByEvent_IdAndUser_Id(EVENT_ID, USER_ID)).thenReturn(Optional.of(rsvp));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> rsvpService.cancelRsvp(USER_ID, EVENT_ID)
        );
        assertEquals(NOT_FOUND, ex.getStatusCode());
        assertEquals("No active RSVP found for this user and event.", ex.getReason());
    }

    @Test
    void getMyRsvps_invalidStatus_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> rsvpService.getMyRsvps(USER_ID, "not_a_status", 0, 25)
        );
        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid status filter.", ex.getReason());
    }

    @Test
    void getMyRsvps_normalizesPageAndSize_andUsesSqlPagination() {
        when(rsvpRepository.findMyUpcomingAll(eq(USER_ID), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        when(rsvpRepository.findMyPastAll(eq(USER_ID), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        MyRsvpsResponse response = rsvpService.getMyRsvps(USER_ID, null, -5, 0);

        assertNotNull(response);
        assertNotNull(response.upcoming());
        assertNotNull(response.past());
        assertEquals(0, response.upcoming().page());
        assertEquals(25, response.upcoming().size());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(rsvpRepository).findMyUpcomingAll(eq(USER_ID), any(), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertEquals(0, used.getPageNumber());
        assertEquals(25, used.getPageSize());
    }

    @Test
    void createRsvp_happyPath_refreshesDbManagedTimestamps() {
        Profile user = Profile.builder()
                .id(USER_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .role(Role.user)
                .build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Event event = Event.builder()
                .id(EVENT_ID)
                .status(EventStatus.active)
                .eventType(EventType.in_person)
                .admissionType(AdmissionType.free)
                .province(Province.ON)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .rsvpCount(0)
                .maxCapacity(10)
                .build();
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        when(rsvpRepository.findByEvent_IdAndUser_Id(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        Rsvp saved = Rsvp.builder()
                .id(RSVP_ID)
                .event(event)
                .user(user)
                .status(RsvpStatus.confirmed)
                .createdAt(null)
                .updatedAt(null)
                .build();
        when(rsvpRepository.save(any(Rsvp.class))).thenReturn(saved);

        RsvpResponse response = rsvpService.createRsvp(USER_ID, EVENT_ID);

        verify(entityManager).refresh(saved);
        assertNotNull(response);
        assertEquals(RSVP_ID, response.id());
        assertEquals(EVENT_ID, response.eventId());
        assertEquals(USER_ID, response.userId());
        assertEquals("confirmed", response.status());
        assertNull(response.createdAt());
        assertNull(response.updatedAt());
    }
}

