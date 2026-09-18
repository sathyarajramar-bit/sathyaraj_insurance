package com.insurance.product.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class TestJwt {

    public static final String SECRET = "test-only-jwt-secret-with-at-least-32-characters";

    private TestJwt() {
    }

    public static String bearer(long userId, String email, String... roles) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder()
                .issuer("insurance-platform").subject(String.valueOf(userId))
                .claim("email", email).claim("roles", List.of(roles))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public static String admin() {
        return bearer(1, "admin@x.com", "ADMIN");
    }

    public static String customer() {
        return bearer(42, "jane@x.com", "CUSTOMER");
    }

    public static String service() {
        return bearer(0, "quote-service@internal", "SERVICE");
    }
}
