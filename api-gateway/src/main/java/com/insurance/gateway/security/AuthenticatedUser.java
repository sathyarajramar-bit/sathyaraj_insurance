package com.insurance.gateway.security;

import java.util.List;

/**
 * Identity extracted from a validated JWT. Forwarded to downstream services as headers
 * (see {@link com.insurance.gateway.filter.GatewayHeaders}).
 *
 * @param userId subject claim: the auth-service user id
 * @param email  email claim
 * @param roles  roles claim, e.g. [CUSTOMER], [ADMIN], [AGENT]
 */
public record AuthenticatedUser(String userId, String email, List<String> roles) {

    public String rolesAsHeaderValue() {
        return String.join(",", roles);
    }
}
