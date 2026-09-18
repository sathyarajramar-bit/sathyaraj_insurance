package com.insurance.auth.service;

import com.insurance.auth.client.CreateCustomerRequest;
import com.insurance.auth.client.CustomerProfileClient;
import com.insurance.auth.dto.AuthResponse;
import com.insurance.auth.dto.LoginRequest;
import com.insurance.auth.dto.RegisterRequest;
import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.User;
import com.insurance.auth.mapper.UserMapper;
import com.insurance.auth.repository.UserRepository;
import com.insurance.auth.security.InvalidCredentialsException;
import com.insurance.auth.security.JwtTokenIssuer;
import com.insurance.common.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Registration and session lifecycle.
 *
 * <p>Transaction boundary of {@link #register}: the user row is inserted, then customer-service is
 * called <em>inside</em> the same transaction. If that call fails the insert is rolled back, so a
 * customer never ends up with a login but no profile. The reverse (profile created, then our commit
 * fails) is possible but harmless: customer-service's create is idempotent per userId, so the next
 * registration attempt simply re-uses it. Deliberate trade-off: no distributed transaction, one clear
 * owner per step, an idempotent remote call as the safety net.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final CustomerProfileClient customerProfileClient;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = userRepository.save(User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phone(request.phone())
                .roles(Set.of(Role.CUSTOMER))
                .build());

        customerProfileClient.createProfile(new CreateCustomerRequest(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getPhone()));

        log.info("Registered user {} ({})", user.getId(), user.getEmail());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is locked");
        }
        log.info("User {} logged in", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.rotate(refreshToken);
        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is locked");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueTokens(User user) {
        return AuthResponse.of(
                tokenIssuer.issueAccessToken(user),
                refreshTokenService.issue(user),
                tokenIssuer.accessTokenTtlSeconds(),
                userMapper.toResponse(user));
    }
}
