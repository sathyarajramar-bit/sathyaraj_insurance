package com.insurance.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenVerifierTest {

    private static final String SECRET = "unit-test-secret-that-is-definitely-32-chars-long";

    private final JwtProperties properties = properties(SECRET, "insurance-platform");
    private final JwtTokenVerifier verifier = new JwtTokenVerifier(properties);

    @Test
    void extractsPrincipalFromValidToken() {
        String token = token(SECRET, "insurance-platform", "42", "jane@example.com", List.of("CUSTOMER"), 60);

        AuthenticatedUser user = verifier.verify(token);

        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.email()).isEqualTo("jane@example.com");
        assertThat(user.roles()).containsExactly("CUSTOMER");
        assertThat(user.hasRole("CUSTOMER")).isTrue();
        assertThat(user.hasAnyRole("ADMIN", "AGENT")).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(SECRET, "insurance-platform", "42", "j@x.com", List.of(), -60);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(CredentialsExpiredException.class);
    }

    @Test
    void rejectsWrongIssuer() {
        String token = token(SECRET, "someone-else", "42", "j@x.com", List.of(), 60);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsWrongSignature() {
        String token = token("another-secret-that-is-also-32-characters!!", "insurance-platform", "42", "j@x.com", List.of(), 60);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsNonNumericSubject() {
        String token = token(SECRET, "insurance-platform", "not-a-number", "j@x.com", List.of(), 60);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BadCredentialsException.class);
    }

    static JwtProperties properties(String secret, String issuer) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setIssuer(issuer);
        return p;
    }

    static String token(String secret, String issuer, String subject, String email, List<String> roles, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer).subject(subject)
                .claim("email", email).claim("roles", roles)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
