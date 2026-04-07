package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.PatchUserRoleRequest;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.model.Role;
import com.gatherly.gatherly_api.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AdminUserService {

    private final ProfileRepository profileRepository;

    public AdminUserService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * Sets a user's role to {@code user} or {@code moderator} only. Cannot change accounts whose current role is {@code admin}.
     */
    public Profile updateUserRole(UUID targetUserId, PatchUserRoleRequest request) {
        Profile profile = profileRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        if (profile.getRole() == Role.admin) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot change role for an admin account via this endpoint."
            );
        }

        Role newRole = Role.valueOf(request.role().name());
        profile.applyRole(newRole);
        return profileRepository.save(profile);
    }
}
