package com.insurance.gateway.security;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.insurance.gateway.exception.ErrorResponseWriter;
import com.insurance.gateway.filter.GatewayHeaders;
import com.insurance.gateway.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test: no Spring context, the chain is a lambda that captures what would be forwarded. */
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                new JwtTokenValidator(TestTokens.SECRET),
                new PublicPathMatcher(List.of("POST /api/auth/login")),
                new ErrorResponseWriter(Jackson2ObjectMapperBuilder.json().build()));
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void forwardsIdentityHeadersForValidToken() {
        String token = TestTokens.valid("42", "jane@example.com", List.of("CUSTOMER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(GatewayHeaders.USER_ID)).isEqualTo("42");
        assertThat(headers.getFirst(GatewayHeaders.USER_EMAIL)).isEqualTo("jane@example.com");
        assertThat(headers.getFirst(GatewayHeaders.USER_ROLES)).isEqualTo("CUSTOMER");
        assertThat(exchange.<AuthenticatedUser>getAttribute(JwtAuthenticationFilter.USER_ATTRIBUTE).userId())
                .isEqualTo("42");
    }

    @Test
    void overwritesSpoofedIdentityHeaders() {
        String token = TestTokens.valid("42", "jane@example.com", List.of("CUSTOMER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(GatewayHeaders.USER_ID, "1")
                .header(GatewayHeaders.USER_ROLES, "ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(GatewayHeaders.USER_ID)).containsExactly("42");
        assertThat(headers.get(GatewayHeaders.USER_ROLES)).containsExactly("CUSTOMER");
    }

    @Test
    void stripsIdentityHeadersOnPublicPathsWithoutRequiringToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/auth/login")
                .header(GatewayHeaders.USER_ID, "1"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(GatewayHeaders.USER_ID)).isFalse();
    }

    @Test
    void missingTokenIsRejectedWith401Json() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/policies"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"error\":\"UNAUTHORIZED\"")
                .contains("\"path\":\"/api/policies\"");
    }

    @Test
    void expiredTokenIsRejectedWithSpecificMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expired("42")));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Token has expired");
    }

    @Test
    void nonBearerSchemeIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/policies")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
