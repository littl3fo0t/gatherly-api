package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for CRUD operations on {@link Profile}.
 */
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
}
