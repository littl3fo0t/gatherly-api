package com.gatherly.gatherly_api.controller;

import com.gatherly.gatherly_api.config.CorsConfig;
import com.gatherly.gatherly_api.config.SecurityConfig;
import com.gatherly.gatherly_api.model.Category;
import com.gatherly.gatherly_api.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@Execution(ExecutionMode.SAME_THREAD)
class CategoryControllerTest {

    /**
     * A tiny shared "test data store".
     * <p>
     * The controller calls {@link CategoryService#getAllCategories()}, so in this test we provide a
     * stub CategoryService implementation that returns whatever is currently stored here.
     */
    private static final AtomicReference<List<Category>> CATEGORIES =
            new AtomicReference<>(Collections.emptyList());

    /**
     * Lock to prevent test methods from racing if the build tool/IDE enables parallel test execution.
     * <p>
     * The controller stub reads from {@link #CATEGORIES}, so we must ensure:
     * 1) one test sets it, and
     * 2) its assertions run,
     * before another test overwrites it.
     */
    private static final Object TEST_LOCK = new Object();

    /**
     * Spring injects a MockMvc instance here so we can simulate HTTP requests against the controller
     * without starting a real server.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Defines a minimal Spring bean to satisfy the controller's dependency on {@link CategoryService}.
     * <p>
     * We intentionally avoid {@code @MockBean} because of dependency/classpath differences in this project.
     */
    @TestConfiguration
    static class TestCategoryServiceConfig {
        @Bean
        CategoryService categoryService() {
            // We avoid @MockBean (not present in this project's test dependencies).
            // Instead, we provide a lightweight stub that returns whatever
            // the test stores in CATEGORIES.
            return new CategoryService(null) {
                @Override
                public List<Category> getAllCategories() {
                    return CATEGORIES.get();
                }
            };
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return tokenValue -> {
                Instant now = Instant.now();
                return Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .subject("test-user")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .build();
            };
        }
    }

    @Test
    void optionsApiCategories_preflight_includesAllowOriginForLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/categories")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void getAllCategories_returns200AndJson() throws Exception {
        // Arrange: create a predictable list of categories.
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        OffsetDateTime createdAt1 = OffsetDateTime.parse("2026-03-16T15:00:00Z");
        OffsetDateTime createdAt2 = OffsetDateTime.parse("2026-03-16T16:00:00Z");

        Category c1 = Category.builder()
                .id(id1)
                .name("Meetup")
                .slug("meetup")
                .createdAt(createdAt1)
                .build();

        Category c2 = Category.builder()
                .id(id2)
                .name("Family-Focused")
                .slug("family-focused")
                .createdAt(createdAt2)
                .build();

        synchronized (TEST_LOCK) {
            // Make the stub service return these categories for this test run.
            CATEGORIES.set(List.of(c1, c2));

            // Act + Assert: call GET /api/categories and verify the JSON fields we care about.
            mockMvc.perform(get("/api/categories").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(id1.toString()))
                    .andExpect(jsonPath("$[0].name").value("Meetup"))
                    .andExpect(jsonPath("$[0].slug").value("meetup"))
                    .andExpect(jsonPath("$[1].id").value(id2.toString()))
                    .andExpect(jsonPath("$[1].name").value("Family-Focused"))
                    .andExpect(jsonPath("$[1].slug").value("family-focused"));
        }
    }

    @Test
    void getAllCategories_emptyList_returnsEmptyArray() throws Exception {
        synchronized (TEST_LOCK) {
            // Arrange: empty response case.
            CATEGORIES.set(Collections.emptyList());

            // Act + Assert: the endpoint should respond with a JSON array.
            mockMvc.perform(get("/api/categories").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }
}

