package com.gatherly.gatherly_api.repository;

import com.gatherly.gatherly_api.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Category} entities.
 * <p>
 * By extending {@code JpaRepository}, we automatically get common CRUD methods
 * such as {@code findAll()}, {@code findById()}, {@code save()}, and {@code delete()}.
 * No implementation class is needed – Spring generates it at runtime.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {
}

