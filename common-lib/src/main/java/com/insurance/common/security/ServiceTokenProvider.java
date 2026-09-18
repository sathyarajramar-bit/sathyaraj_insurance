package com.insurance.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Identity of this service when it calls another service (no user context, or a user context that must
 * not be forwarded). The token has {@code sub=0}, {@code roles=[SERVICE]} and a 5 minute lifetime; it is
 * cached and renewed one minute before expiry. Verified by the same {@link JwtTokenVerifier} as user tokens.
 *
 * <p>Trade-off: forwarding the caller's own JWT would let downstream services apply the user's rights
 * directly, but it couples services to the user session (expiry mid-flow, background jobs have no user).
 * A service identity plus explicit "SERVICE may read X" rules downstream is simpler to reason about.
 */
public class ServiceTokenProvider {

    public static final String SERVICE_SUBJECT = "0";
    public static final String SERVICE_ROLE = "SERVICE";

    private final JwtProperties properties;
    private final SecretKey key;
    private final String serviceName;
    private volatile String cachedToken;
    private volatile Instant renewAfter = Instant.EPOCH;

    public ServiceTokenProvider(JwtProperties properties, String serviceName) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.serviceName = serviceName;
    }

    public String getToken() {
        if (cachedToken == null || Instant.now().isAfter(renewAfter)) {
            synchronized (this) {
                if (cachedToken == null || Instant.now().isAfter(renewAfter)) {
                    Instant now = Instant.now();
                    cachedToken = Jwts.builder()
                            .issuer(properties.getIssuer())
                            .subject(SERVICE_SUBJECT)
                            .claim(JwtTokenVerifier.CLAIM_EMAIL, serviceName + "@internal")
                            .claim(JwtTokenVerifier.CLAIM_ROLES, List.of(SERVICE_ROLE))
                            .issuedAt(Date.from(now))
                            .expiration(Date.from(now.plusSeconds(300)))
                            .signWith(key)
                            .compact();
                    renewAfter = now.plusSeconds(240);
                }
            }
        }
        return cachedToken;
    }
}
