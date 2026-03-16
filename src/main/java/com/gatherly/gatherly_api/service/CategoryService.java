package com.gatherly.gatherly_api.service;

import com.gatherly.gatherly_api.model.Category;
import com.gatherly.gatherly_api.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service that contains the domain logic related to categories.
 * <p>
 * Right now this is just a thin layer over the repository, but putting the
 * logic here keeps controllers simple and gives us a place to add caching,
 * validation, or cross‑cutting concerns later without changing the web layer.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Spring will inject an implementation of {@link CategoryRepository}
     * through this constructor (constructor injection is the recommended style).
     */
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Fetches every category from the database.
     * <p>
     * This method is intentionally simple: each call runs a fresh query and
     * returns the current list of rows, keeping the API stateless.
     */
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
}

