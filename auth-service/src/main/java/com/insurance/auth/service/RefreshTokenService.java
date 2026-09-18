package com.insurance.auth.service;

import com.insurance.auth.entity.RefreshToken;
import com.insurance.auth.entity.User;
import com.insurance.auth.repository.RefreshTokenRepository;
import com.insurance.auth.security.InvalidCredentialsException;
import com.insurance.common.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes opaque refresh tokens. The database only ever holds the SHA-256 of a
 * token, so a leaked database dump cannot be used to mint sessions.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final SecureRandom random = new SecureRandom();

    /** @return the raw token to hand to the client (never stored). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtl()))
                .build());
        return raw;
    }

    /**
     * Validates the presented token, revokes it and returns its user so the caller can issue a new pair.
     * Reuse of an already revoked token is treated as a compromise: every token of that user is revoked.
     */
    @Transactional
    public User rotate(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token is invalid"));
        if (token.isRevoked()) {
            repository.revokeAllForUser(token.getUser().getId());
            throw new InvalidCredentialsException("Refresh token has been revoked");
        }
        if (!token.isUsable(Instant.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }
        token.setRevoked(true);
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.setRevoked(true));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
