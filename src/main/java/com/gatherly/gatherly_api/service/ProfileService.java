package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.dto.UpdateMyProfileRequest;
import com.gatherly.gatherly_api.model.Profile;
import com.gatherly.gatherly_api.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Handles profile-related application logic.
 */
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public Profile getMyProfile(UUID userId) {
        return findRequiredProfile(userId);
    }

    public Profile updateMyProfile(UUID userId, UpdateMyProfileRequest request) {
        Profile profile = findRequiredProfile(userId);
        profile.applySelfServiceUpdate(
                request.fullName(),
                request.avatarUrl(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        return profileRepository.save(profile);
    }

    private Profile findRequiredProfile(UUID userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile not found for authenticated user."
                ));
    }
}
