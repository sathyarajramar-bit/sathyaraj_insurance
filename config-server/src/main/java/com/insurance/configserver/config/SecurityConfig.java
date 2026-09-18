package com.insurance.configserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Protects configuration endpoints with HTTP Basic authentication.
 *
 * <p>Configuration files describe the whole topology and contain placeholders for secrets, so they
 * must not be readable anonymously. Clients authenticate with
 * {@code spring.cloud.config.username/password}. Health stays public so Docker/Kubernetes probes work.
 *
 * <p>The user is defined by {@code spring.security.user.name/password} (see application.yml), which
 * come from environment variables.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Machine-to-machine API: no browser sessions, no CSRF tokens.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
