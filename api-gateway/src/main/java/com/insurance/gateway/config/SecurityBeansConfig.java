package com.insurance.gateway.config;

import com.insurance.gateway.security.JwtTokenValidator;
import com.insurance.gateway.security.PublicPathMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the plain (framework-free) security collaborators from configuration.
 * Keeping them as simple classes makes them trivially unit-testable.
 */
@Configuration
public class SecurityBeansConfig {

    @Bean
    JwtTokenValidator jwtTokenValidator(GatewaySecurityProperties properties) {
        return new JwtTokenValidator(properties.getJwtSecret());
    }

    @Bean
    PublicPathMatcher publicPathMatcher(GatewaySecurityProperties properties) {
        return new PublicPathMatcher(properties.getPublicPaths());
    }
}
