package com.insurance.auth.service;

import com.insurance.auth.client.CreateCustomerRequest;
import com.insurance.auth.client.CustomerProfileClient;
import com.insurance.auth.dto.AuthResponse;
import com.insurance.auth.dto.LoginRequest;
import com.insurance.auth.dto.RegisterRequest;
import com.insurance.auth.dto.UserResponse;
import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.User;
import com.insurance.auth.entity.UserStatus;
import com.insurance.auth.mapper.UserMapper;
import com.insurance.auth.repository.UserRepository;
import com.insurance.auth.security.InvalidCredentialsException;
import com.insurance.auth.security.JwtTokenIssuer;
import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenIssuer tokenIssuer;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CustomerProfileClient customerProfileClient;
    @Mock private UserMapper userMapper;
    @InjectMocks private AuthService authService;

    private final RegisterRequest register = new RegisterRequest("Jane@Example.com", "Passw0rd", " Jane ", "Doe", "9876543210");

    @Test
    void registerHashesPasswordAssignsCustomerRoleAndCreatesProfile() {
        when(userRepository.existsByEmailIgnoreCase("Jane@Example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 42L);
            return u;
        });
        when(tokenIssuer.issueAccessToken(any())).thenReturn("access");
        when(tokenIssuer.accessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(any())).thenReturn("refresh");
        when(userMapper.toResponse(any())).thenReturn(new UserResponse(42L, "jane@example.com", "Jane", "Doe", null,
                UserStatus.ACTIVE, Set.of(Role.CUSTOMER), null));

        AuthResponse response = authService.register(register);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(saved.getValue().getRoles()).containsExactly(Role.CUSTOMER);

        ArgumentCaptor<CreateCustomerRequest> profile = ArgumentCaptor.forClass(CreateCustomerRequest.class);
        verify(customerProfileClient).createProfile(profile.capture());
        assertThat(profile.getValue().userId()).isEqualTo(42L);
        assertThat(profile.getValue().email()).isEqualTo("jane@example.com");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
    }

    @Test
    void registerRejectsDuplicateEmailBeforeTouchingAnythingElse() {
        when(userRepository.existsByEmailIgnoreCase("Jane@Example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(register)).isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
        verify(customerProfileClient, never()).createProfile(any());
    }

    @Test
    void registerPropagatesProfileFailureSoTheTransactionRollsBack() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("h");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerProfileClient.createProfile(any())).thenThrow(new ServiceUnavailableException("customer-service"));

        assertThatThrownBy(() -> authService.register(register)).isInstanceOf(ServiceUnavailableException.class);

        verify(tokenIssuer, never()).issueAccessToken(any());
    }

    @Test
    void loginWithWrongPasswordFailsWithGenericMessage() {
        User user = User.builder().email("jane@example.com").passwordHash("h").firstName("J").lastName("D").build();
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "h")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginOfLockedAccountIsRejected() {
        User user = User.builder().email("jane@example.com").passwordHash("h").firstName("J").lastName("D")
                .status(UserStatus.LOCKED).build();
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd", "h")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "Passw0rd")))
                .hasMessage("Account is locked");
    }
}
