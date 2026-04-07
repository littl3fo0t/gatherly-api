package com.gatherly.gatherly_api.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

/**
 * Builds the JWT decoder from the OIDC issuer. Normalizes the issuer string so it matches
 * the {@code iss} claim in Supabase access tokens (no trailing slash; Supabase uses
 * {@code https://&lt;project-ref&gt;.supabase.co/auth/v1}).
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "issuer-uri")
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        String normalized = issuerUri.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri must not be blank");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Invalid spring.security.oauth2.resourceserver.jwt.issuer-uri: " + normalized, e);
        }

        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri must use https (got scheme: "
                            + (scheme == null ? "<missing>" : scheme)
                            + ")");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri must be an absolute https URL, e.g. https://<project-ref>.supabase.co/auth/v1");
        }
        if (host.startsWith("postgres.")) {
            throw new IllegalStateException(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri is pointing at a Supabase Postgres host ("
                            + host
                            + "). It must point at the Supabase Auth issuer, e.g. https://<project-ref>.supabase.co/auth/v1");
        }
        if (!normalized.endsWith("/auth/v1")) {
            throw new IllegalStateException(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri must end with /auth/v1 (got: "
                            + normalized
                            + ")");
        }
        return JwtDecoders.fromIssuerLocation(normalized);
    }
}
