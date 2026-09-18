package com.insurance.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Verifies access tokens inside a service (defence in depth behind the gateway).
 *
 * <p>Same contract as the gateway: {@code sub}=user id, {@code email}, {@code roles}. Failures are
 * reported as Spring Security {@code AuthenticationException}s so the entry point renders a 401.
 */
public class JwtTokenVerifier {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";

    private final JwtParser parser;

    public JwtTokenVerifier(JwtProperties properties) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(properties.getIssuer())
                .build();
    }

    public AuthenticatedUser verify(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new CredentialsExpiredException("Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid token");
        }
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Invalid token subject");
        }
        Object rawRoles = claims.get(CLAIM_ROLES);
        List<String> roles = rawRoles instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        return new AuthenticatedUser(userId, claims.get(CLAIM_EMAIL, String.class), roles);
    }
}
