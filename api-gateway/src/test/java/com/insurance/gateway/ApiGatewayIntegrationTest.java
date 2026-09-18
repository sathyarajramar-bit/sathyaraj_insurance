package com.insurance.gateway;

import com.insurance.gateway.filter.GatewayHeaders;
import com.insurance.gateway.support.EchoController;
import com.insurance.gateway.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;

/**
 * Boots the real gateway (Netty, routing, filters, error handling) on a fixed port with routes that
 * loop back into an in-process {@link EchoController}, so the whole edge behaviour is exercised
 * without Eureka, the Config Server or any downstream service.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@Import(EchoController.class)
class ApiGatewayIntegrationTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:18080")
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void protectedRouteWithoutTokenReturns401InStandardFormat() {
        client.get().uri("/secure/policies")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectHeader().exists(GatewayHeaders.CORRELATION_ID)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Missing or malformed Authorization header")
                .jsonPath("$.path").isEqualTo("/secure/policies")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void expiredTokenReturns401() {
        client.get().uri("/secure/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expired("42"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Token has expired");
    }

    @Test
    void validTokenIsTranslatedIntoIdentityHeadersForDownstream() {
        String token = TestTokens.valid("42", "jane@example.com", List.of("CUSTOMER", "AGENT"));

        client.get().uri("/secure/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(GatewayHeaders.CORRELATION_ID, "corr-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(GatewayHeaders.CORRELATION_ID, "corr-123")
                .expectBody()
                .jsonPath("$['X-User-Id']").isEqualTo("42")
                .jsonPath("$['X-User-Email']").isEqualTo("jane@example.com")
                .jsonPath("$['X-User-Roles']").isEqualTo("CUSTOMER,AGENT")
                .jsonPath("$['X-Correlation-Id']").isEqualTo("corr-123");
    }

    @Test
    void clientSuppliedIdentityHeadersCannotEscalatePrivileges() {
        String token = TestTokens.valid("42", "jane@example.com", List.of("CUSTOMER"));

        client.get().uri("/secure/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(GatewayHeaders.USER_ID, "1")
                .header(GatewayHeaders.USER_ROLES, "ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$['X-User-Id']").isEqualTo("42")
                .jsonPath("$['X-User-Roles']").isEqualTo("CUSTOMER");
    }

    @Test
    void publicPathNeedsNoTokenAndGetsGeneratedCorrelationId() {
        client.get().uri("/public/products")
                .header(GatewayHeaders.USER_ID, "spoofed")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(GatewayHeaders.CORRELATION_ID)
                .expectBody()
                .jsonPath("$['X-User-Id']").doesNotExist()
                .jsonPath("$['X-Correlation-Id']").isNotEmpty();
    }

    @Test
    void publicRuleIsMethodSpecific() {
        client.post().uri("/public/products")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unknownRouteReturns404InStandardFormat() {
        client.get().uri("/no-such-route")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("NOT_FOUND")
                .jsonPath("$.path").isEqualTo("/no-such-route");
    }

    @Test
    void downstreamServiceUnavailableReturns503InStandardFormat() {
        String token = TestTokens.valid("42", "jane@example.com", List.of("CUSTOMER"));

        client.get().uri("/down/anything")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.path").isEqualTo("/down/anything");
    }

    @Test
    void actuatorHealthIsServedByTheGatewayItself() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
