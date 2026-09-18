package com.insurance.auth.service;

import com.insurance.auth.entity.RefreshToken;
import com.insurance.auth.entity.User;
import com.insurance.auth.repository.RefreshTokenRepository;
import com.insurance.auth.security.InvalidCredentialsException;
import com.insurance.common.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-jwt-secret-with-at-least-32-characters");
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        service = new RefreshTokenService(repository, properties);
        user = User.builder().email("jane@example.com").passwordHash("x").firstName("J").lastName("D").build();
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void issueStoresOnlyTheHashOfTheToken() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(raw).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void rotateRevokesThePresentedTokenAndReturnsItsUser() {
        RefreshToken token = RefreshToken.builder().user(user).tokenHash(RefreshTokenService.hash("raw"))
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

        assertThat(service.rotate("raw")).isSameAs(user);
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void reuseOfRevokedTokenRevokesAllTokensOfTheUser() {
        RefreshToken token = RefreshToken.builder().user(user).tokenHash("h").expiresAt(Instant.now().plusSeconds(60))
                .revoked(true).build();
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.rotate("raw"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token has been revoked");
        verify(repository).revokeAllForUser(7L);
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken token = RefreshToken.builder().user(user).tokenHash("h").expiresAt(Instant.now().minusSeconds(1)).build();
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.rotate("raw")).hasMessage("Refresh token has expired");
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("raw")).hasMessage("Refresh token is invalid");
    }
}
