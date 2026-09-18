package com.insurance.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Verifies JWT signature and expiry and extracts the identity claims.
 *
 * <p>Token contract (issued by auth-service in Phase 2):
 * <pre>
 *   sub   : user id
 *   email : user email
 *   roles : ["CUSTOMER" | "ADMIN" | "AGENT", ...]
 *   iat / exp : standard timestamps
 * </pre>
 *
 * <p>Interview notes:
 * <ul>
 *   <li><b>Stateless auth:</b> the signature proves the token was issued by auth-service; no database
 *       or session lookup is needed per request, which is what makes horizontal scaling trivial.</li>
 *   <li><b>HS256 vs RS256:</b> HS256 uses one shared secret (simple, but every verifier could also
 *       forge tokens). RS256 lets only auth-service sign (private key) while everyone verifies with the
 *       public key, typically fetched from a JWKS endpoint. Swap this class to switch algorithms.</li>
 *   <li><b>Revocation:</b> a JWT cannot be revoked before {@code exp}. Mitigations: short-lived access
 *       tokens plus refresh tokens, or a Redis deny-list of revoked token ids checked here.</li>
 * </ul>
 */
@Slf4j
public class JwtTokenValidator {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLES = "roles";
    private static final String DEV_SECRET_PREFIX = "insurance-dev-only";

    private final JwtParser parser;

    public JwtTokenValidator(String secret) {
        if (secret.startsWith(DEV_SECRET_PREFIX)) {
            log.warn("Using the DEVELOPMENT JWT secret. Set JWT_SECRET for any shared or production environment.");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
    }

    /**
     * @param token the raw compact JWT (without the "Bearer " prefix)
     * @return the identity carried by the token
     * @throws InvalidTokenException if the token is expired, tampered with, or malformed
     */
    public AuthenticatedUser validate(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT rejected: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidTokenException("Token has no subject");
        }
        return new AuthenticatedUser(subject, claims.get(CLAIM_EMAIL, String.class), extractRoles(claims));
    }

    private static List<String> extractRoles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }
}
