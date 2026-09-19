package com.insurance.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Security settings bound from {@code gateway.security.*} (served by the Config Server).
 *
 * <p>Validation runs at startup: a missing or short JWT secret fails fast with a clear message
 * instead of producing "invalid signature" errors for every request later.
 * 
 * 
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    /** HMAC secret shared with auth-service. HS256 requires at least 256 bits (32 bytes). */
    @NotBlank
    @Size(min = 32, message = "gateway.security.jwt-secret must be at least 32 characters (256 bits)")
    private String jwtSecret;

    /**
     * Endpoints that do not require a token.
     * Entries are either {@code "<METHOD> <pattern>"} (e.g. {@code "GET /api/products/**"}) or just
     * {@code "<pattern>"} to allow every method.
     */
    private List<String> publicPaths = new ArrayList<>();
}
