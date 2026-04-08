package com.gatherly.gatherly_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;
import java.util.regex.Pattern;

import com.gatherly.gatherly_api.security.RestAccessDeniedHandler;
import com.gatherly.gatherly_api.security.RestAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    /**
     * Public event detail: {@code GET /api/events/{uuid}} — excludes paths like {@code /api/events/my}
     * so JWTs are still validated there.
     */
    private static final Pattern PUBLIC_EVENT_DETAIL_PATH = Pattern.compile(
            "^/api/events/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    /**
     * {@code permitAll} reads where the handler does not use authentication; ignore any {@code Authorization}
     * header so a stale or malformed bearer token cannot trigger JWT processing (401/500) on a public route.
     */
    private static boolean isPublicReadIgnoringBearer(String method, String path) {
        if (!"GET".equalsIgnoreCase(method) || path == null) {
            return false;
        }
        if (PUBLIC_EVENT_DETAIL_PATH.matcher(path).matches()) {
            return true;
        }
        return "/api/events".equals(path)
                || "/api/events/".equals(path)
                || "/api/categories".equals(path)
                || "/api/categories/".equals(path);
    }

    /**
     * Resolves application role from the JWT.
     *
     * Supabase sets top-level {@code role} to {@code authenticated}/{@code anon}.
     * The app role (user|moderator|admin) is added by {@code custom_access_token_hook}
     * under {@code app_metadata.role}.
     *
     * For backwards compatibility with older tokens/tests, we also fall back to
     * {@code user_metadata.role} and then top-level {@code role} (excluding
     * {@code authenticated}/{@code anon}).
     * Test tokens may set top-level {@code role} directly.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = applicationRoleFromJwt(jwt);
            if (role == null || role.isBlank()) {
                return java.util.List.<GrantedAuthority>of();
            }
            String normalized = role.trim().toUpperCase();
            return java.util.List.<GrantedAuthority>of(
                    new SimpleGrantedAuthority("ROLE_" + normalized)
            );
        });
        return converter;
    }

    private static String applicationRoleFromJwt(org.springframework.security.oauth2.jwt.Jwt jwt) {
        Map<String, Object> appMetadata = jwt.getClaim("app_metadata");
        if (appMetadata != null) {
            Object r = appMetadata.get("role");
            if (r instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
        if (userMetadata != null) {
            Object r = userMetadata.get("role");
            if (r instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        String top = jwt.getClaimAsString("role");
        if (top == null || top.isBlank()) {
            return null;
        }
        if ("authenticated".equalsIgnoreCase(top) || "anon".equalsIgnoreCase(top)) {
            return null;
        }
        return top;
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(Environment environment) {
        return new RestAuthenticationEntryPoint(environment);
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler(Environment environment) {
        return new RestAccessDeniedHandler(environment);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                // Stateless API: the client sends a token on every request.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // When the token is missing/invalid -> 401 (JSON).
                        .accessDeniedHandler(accessDeniedHandler)
                        // When the token is valid but user lacks permissions -> 403 (JSON).
                )
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight has no Authorization header; must not hit JWT authentication.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html").permitAll()
                        // List routes: with and without trailing slash (must match isPublicReadIgnoringBearer).
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/events/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Validate JWTs for any endpoint that requires authentication.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicEventReadBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // If the token is missing/invalid, Spring will use our JSON 401 handler.
                        .authenticationEntryPoint(authenticationEntryPoint)
                );

        return http.build();
    }

    /**
     * For public GET routes that do not require auth, omit the bearer token so Spring does not run JWT
     * validation. Matches {@code getEventById} behavior (optional token decoded in the controller).
     */
    @Bean
    public BearerTokenResolver publicEventReadBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            String method = request.getMethod();
            if (isPublicReadIgnoringBearer(method, path)) {
                return null;
            }
            return delegate.resolve(request);
        };
    }
}

