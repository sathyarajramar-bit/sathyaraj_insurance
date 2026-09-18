package com.insurance.gateway.filter;

/**
 * Headers the gateway adds to downstream requests.
 *
 * <p>Downstream services are only reachable through the gateway (private Docker network / VPC),
 * so they can trust these headers. The gateway strips any client-supplied values before adding its own,
 * which is what prevents header spoofing.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private GatewayHeaders() {
    }
}
