package com.insurance.gateway.security;

import com.insurance.gateway.exception.ErrorResponseWriter;
import com.insurance.gateway.filter.GatewayHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authenticates every routed request exactly once, at the edge.
 *
 * <pre>
 *  Client ──Authorization: Bearer &lt;jwt&gt;──▶ Gateway ──X-User-Id / X-User-Email / X-User-Roles──▶ Service
 * </pre>
 *
 * <p>Decision made here: the gateway performs <b>authentication</b> (who is calling) and forwards the
 * identity as headers; each service performs <b>authorization</b> (may this user do this to this
 * resource), because ownership rules ("customer can only see own policies") need the service's data.
 * Services will additionally re-validate the JWT in Phase 2 for defence in depth, so a request that
 * bypasses the gateway inside the network is still rejected.
 *
 * <p>This is a {@link GlobalFilter}, so it applies to every route without repeating it per route.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** Runs before any route filter (RewritePath etc.), so public path rules see the original path. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final String USER_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".user";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator tokenValidator;
    private final PublicPathMatcher publicPathMatcher;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().pathWithinApplication().value();

        // CORS pre-flight never carries credentials; the CorsWebFilter answers it before we get here anyway.
        if (HttpMethod.OPTIONS.equals(method) || publicPathMatcher.isPublic(method, path)) {
            return chain.filter(exchange.mutate().request(stripIdentityHeaders(request)).build());
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Missing or malformed Authorization header");
        }

        AuthenticatedUser user;
        try {
            user = tokenValidator.validate(authorization.substring(BEARER_PREFIX.length()));
        } catch (InvalidTokenException e) {
            log.info("Rejected {} {}: {}", method, path, e.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        }

        exchange.getAttributes().put(USER_ATTRIBUTE, user);
        ServerHttpRequest authenticatedRequest = request.mutate()
                .headers(headers -> {
                    removeIdentityHeaders(headers);
                    headers.set(GatewayHeaders.USER_ID, user.userId());
                    if (user.email() != null) {
                        headers.set(GatewayHeaders.USER_EMAIL, user.email());
                    }
                    headers.set(GatewayHeaders.USER_ROLES, user.rolesAsHeaderValue());
                })
                .build();
        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    private static ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate().headers(JwtAuthenticationFilter::removeIdentityHeaders).build();
    }

    private static void removeIdentityHeaders(HttpHeaders headers) {
        headers.remove(GatewayHeaders.USER_ID);
        headers.remove(GatewayHeaders.USER_EMAIL);
        headers.remove(GatewayHeaders.USER_ROLES);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
