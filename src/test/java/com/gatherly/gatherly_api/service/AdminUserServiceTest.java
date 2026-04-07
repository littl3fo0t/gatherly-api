package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.PatchUserRoleRequest;
import com.gatherly.gatherly_api.dto.PromotableRole;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void updateUserRole_promotesUserToModerator() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Profile existing = Profile.builder()
                .id(id)
                .fullName("A")
                .email("a@example.com")
                .role(Role.user)
                .build();
        when(profileRepository.findById(id)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = adminUserService.updateUserRole(id, new PatchUserRoleRequest(PromotableRole.moderator));

        assertEquals(Role.moderator, result.getRole());
        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        assertEquals(Role.moderator, captor.getValue().getRole());
    }

    @Test
    void updateUserRole_demotesModeratorToUser() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Profile existing = Profile.builder()
                .id(id)
                .fullName("B")
                .email("b@example.com")
                .role(Role.moderator)
                .build();
        when(profileRepository.findById(id)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = adminUserService.updateUserRole(id, new PatchUserRoleRequest(PromotableRole.user));

        assertEquals(Role.user, result.getRole());
    }

    @Test
    void updateUserRole_whenMissing_throws404() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(profileRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.updateUserRole(id, new PatchUserRoleRequest(PromotableRole.moderator))
        );
        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateUserRole_whenTargetIsAdmin_throws400() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Profile existing = Profile.builder()
                .id(id)
                .fullName("Admin")
                .email("admin@example.com")
                .role(Role.admin)
                .build();
        when(profileRepository.findById(id)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.updateUserRole(id, new PatchUserRoleRequest(PromotableRole.moderator))
        );
        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }
}
