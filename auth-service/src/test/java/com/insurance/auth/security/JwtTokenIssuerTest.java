package com.insurance.auth.security;

import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.User;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.JwtProperties;
import com.insurance.common.security.JwtTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trip: what auth-service signs must be exactly what common-lib (and the gateway) verify. */
class JwtTokenIssuerTest {

    private final JwtProperties properties = properties();
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(properties);
    private final JwtTokenVerifier verifier = new JwtTokenVerifier(properties);

    @Test
    void accessTokenCarriesIdEmailAndSortedRoles() {
        User user = User.builder().email("jane@example.com").passwordHash("x").firstName("Jane").lastName("Doe")
                .roles(Set.of(Role.AGENT, Role.CUSTOMER)).build();
        ReflectionTestUtils.setField(user, "id", 42L);

        AuthenticatedUser principal = verifier.verify(issuer.issueAccessToken(user));

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("jane@example.com");
        assertThat(principal.roles()).containsExactly("AGENT", "CUSTOMER");
        assertThat(issuer.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void serviceTokenHasServiceRoleAndSubjectZero() {
        AuthenticatedUser principal = verifier.verify(issuer.issueServiceToken("auth-service"));

        assertThat(principal.userId()).isZero();
        assertThat(principal.email()).isEqualTo("auth-service@internal");
        assertThat(principal.roles()).containsExactly("SERVICE");
    }

    private static JwtProperties properties() {
        JwtProperties p = new JwtProperties();
        p.setSecret("test-only-jwt-secret-with-at-least-32-characters");
        p.setIssuer("insurance-platform");
        p.setAccessTokenTtl(Duration.ofMinutes(15));
        return p;
    }
}
