package com.gatherly.gatherly_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
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
     * Public {@code GET /api/events/{uuid}} only — excludes paths like {@code /api/events/my} so JWTs are validated there.
     */
    private static final Pattern PUBLIC_EVENT_DETAIL_PATH = Pattern.compile(
            "^/api/events/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // When the token is missing/invalid -> 401 (JSON).
                        .accessDeniedHandler(accessDeniedHandler)
                        // When the token is valid but user lacks permissions -> 403 (JSON).
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/events/*").permitAll()
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
     * Public event reads should not fail on invalid tokens; controller handles token best-effort.
     */
    @Bean
    public BearerTokenResolver publicEventReadBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method) && path != null && PUBLIC_EVENT_DETAIL_PATH.matcher(path).matches()) {
                return null;
            }
            return delegate.resolve(request);
        };
    }
}

