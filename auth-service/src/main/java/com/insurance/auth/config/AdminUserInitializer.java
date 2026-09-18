package com.insurance.auth.config;

import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.User;
import com.insurance.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Seeds the first ADMIN so the platform is usable out of the box (Flyway cannot do it: the BCrypt hash
 * would have to be committed). Idempotent: does nothing when the account already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getEmail() == null || properties.getPassword() == null || properties.getPassword().isBlank()) {
            log.info("No admin bootstrap configured (auth.admin.email / ADMIN_PASSWORD); skipping");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(properties.getEmail())) {
            return;
        }
        userRepository.save(User.builder()
                .email(properties.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(properties.getPassword()))
                .firstName("Platform")
                .lastName("Admin")
                .roles(Set.of(Role.ADMIN))
                .build());
        log.info("Bootstrapped ADMIN account {}", properties.getEmail());
    }
}
