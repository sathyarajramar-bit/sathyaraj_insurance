package com.insurance.gateway.security;

import com.insurance.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private final JwtTokenValidator validator = new JwtTokenValidator(TestTokens.SECRET);

    @Test
    void extractsIdentityFromValidToken() {
        String token = TestTokens.valid("42", "jane@example.com", List.of("CUSTOMER", "AGENT"));

        AuthenticatedUser user = validator.validate(token);

        assertThat(user.userId()).isEqualTo("42");
        assertThat(user.email()).isEqualTo("jane@example.com");
        assertThat(user.roles()).containsExactly("CUSTOMER", "AGENT");
        assertThat(user.rolesAsHeaderValue()).isEqualTo("CUSTOMER,AGENT");
    }

    @Test
    void tokenWithoutRolesYieldsEmptyRoleList() {
        String token = TestTokens.valid("7", "no-roles@example.com", List.of());

        assertThat(validator.validate(token).roles()).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        assertThatThrownBy(() -> validator.validate(TestTokens.expired("42")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token has expired");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String forged = TestTokens.signedWith("a-different-secret-that-is-also-32-chars-long", "42");

        assertThatThrownBy(() -> validator.validate(forged))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid token");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> validator.validate("not.a.jwt"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid token");
    }
}
