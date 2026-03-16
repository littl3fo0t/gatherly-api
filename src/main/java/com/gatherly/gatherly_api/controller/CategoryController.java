package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.model.Category;
import com.gatherly.gatherly_api.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes read‑only endpoints for event categories.
 * <p>
 * This sits at the very edge of the application: it receives HTTP requests,
 * delegates to {@link CategoryService}, and returns JSON responses. It should
 * stay thin and avoid business logic so the same rules can be reused elsewhere
 * (for example, in other controllers or background jobs).
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Public category endpoints")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(
            summary = "List all categories",
            description = "Returns the full list of available event categories.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Returns all categories.",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = Category.class))
                            )
                    )
            }
    )
    /**
     * Handles {@code GET /api/categories}.
     * <p>
     * Because this endpoint is public in the security configuration, clients
     * (web frontends, mobile apps, etc.) can call it without a JWT to populate
     * category pickers when creating or filtering events.
     */
    public ResponseEntity<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories);
    }
}

