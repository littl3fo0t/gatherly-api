package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.UpdateMyProfileRequest;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Province;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void getMyProfile_whenPresent_returnsProfile() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        Profile existing = profile(userId, "Original Name", "original@example.com", "user");
        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));

        Profile result = profileService.getMyProfile(userId);

        assertEquals(userId, result.getId());
        assertEquals("Original Name", result.getFullName());
    }

    @Test
    void updateMyProfile_updatesEditableFieldsOnly() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        Profile existing = profile(userId, "Original Name", "original@example.com", "user");
        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMyProfileRequest request = new UpdateMyProfileRequest(
                "Updated Name",
                "https://example.com/avatar.jpg",
                "456 Queen St",
                "Unit 2",
                "Ottawa",
                "ON",
                "K1A 0A9"
        );

        Profile result = profileService.updateMyProfile(userId, request);

        assertEquals("Updated Name", result.getFullName());
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());
        assertEquals("Ottawa", result.getCity());
        assertEquals("original@example.com", result.getEmail());
        assertEquals(Role.user, result.getRole());

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        assertEquals("Updated Name", captor.getValue().getFullName());
    }

    @Test
    void getMyProfile_whenMissing_throws404() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000033");
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> profileService.getMyProfile(userId)
        );

        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    private static Profile profile(UUID id, String name, String email, String role) {
        return Profile.builder()
                .id(id)
                .fullName(name)
                .email(email)
                .role(Role.valueOf(role))
                .avatarUrl(null)
                .addressLine1("123 King St")
                .addressLine2(null)
                .city("Toronto")
                .province(Province.ON)
                .postalCode("M5V 2T6")
                .createdAt(OffsetDateTime.parse("2026-03-18T10:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-03-18T10:00:00Z"))
                .build();
    }
}
