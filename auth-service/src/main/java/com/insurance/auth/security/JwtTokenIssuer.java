package com.insurance.auth.security;

import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.User;
import com.insurance.common.security.JwtProperties;
import com.insurance.common.security.JwtTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * The only place in the platform that signs JWTs.
 *
 * <p>Token contract (verified by the gateway and every service):
 * {@code iss}=insurance-platform, {@code sub}=user id, {@code email}, {@code roles}=[...], {@code iat}, {@code exp}.
 *
 * <p>Service tokens ({@link #issueServiceToken(String)}) carry the pseudo role SERVICE and subject 0; they let
 * one service call another's internal endpoints without a user context (e.g. profile creation on sign-up).
 */
@Component
public class JwtTokenIssuer {

    /** Subject used for machine identities; no human user ever has id 0 (AUTO_INCREMENT starts at 1). */
    public static final String SERVICE_SUBJECT = "0";
    public static final String SERVICE_ROLE = "SERVICE";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenIssuer(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
        return sign(String.valueOf(user.getId()), user.getEmail(), roles, properties.getAccessTokenTtl().toSeconds());
    }

    public String issueServiceToken(String serviceName) {
        return sign(SERVICE_SUBJECT, serviceName + "@internal", List.of(SERVICE_ROLE), 300);
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    private String sign(String subject, String email, List<String> roles, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(subject)
                .claim(JwtTokenVerifier.CLAIM_EMAIL, email)
                .claim(JwtTokenVerifier.CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
