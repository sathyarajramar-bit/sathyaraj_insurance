package com.insurance.gateway.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Builds JWTs the same way auth-service will, so gateway tests do not depend on that service. */
public final class TestTokens {

    public static final String SECRET = "test-only-jwt-secret-with-at-least-32-characters";

    private TestTokens() {
    }

    public static String valid(String userId, String email, List<String> roles) {
        return build(SECRET, userId, email, roles, Duration.ofMinutes(15));
    }

    public static String expired(String userId) {
        return build(SECRET, userId, "expired@example.com", List.of("CUSTOMER"), Duration.ofMinutes(-5));
    }

    public static String signedWith(String otherSecret, String userId) {
        return build(otherSecret, userId, "other@example.com", List.of("CUSTOMER"), Duration.ofMinutes(15));
    }

    public static String build(String secret, String userId, String email, List<String> roles, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
