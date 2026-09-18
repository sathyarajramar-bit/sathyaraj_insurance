package com.insurance.common.security;

import java.util.List;

/**
 * The principal placed in the Spring Security context after a JWT is verified.
 *
 * @param userId auth-service user id (JWT {@code sub})
 * @param email  JWT {@code email} claim
 * @param roles  JWT {@code roles} claim without the {@code ROLE_} prefix, e.g. CUSTOMER, ADMIN, AGENT, SERVICE
 */
public record AuthenticatedUser(Long userId, String email, List<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String role : candidates) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
