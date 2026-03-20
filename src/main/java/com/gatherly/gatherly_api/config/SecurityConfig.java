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
import org.springframework.security.web.SecurityFilterChain;

import com.gatherly.gatherly_api.security.RestAccessDeniedHandler;
import com.gatherly.gatherly_api.security.RestAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    /**
     * Reads the Supabase JWT claim (named {@code role}) and converts it into
     * Spring Security authorities.
     *
     * Spring Security expects authorities like {@code ROLE_ADMIN}, so we take the
     * JWT's `role` value and turn it into {@code ROLE_} + uppercase role.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
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
                        .anyRequest().authenticated()
                )
                // Validate JWTs for any endpoint that requires authentication.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // If the token is missing/invalid, Spring will use our JSON 401 handler.
                        .authenticationEntryPoint(authenticationEntryPoint)
                );

        return http.build();
    }
}

