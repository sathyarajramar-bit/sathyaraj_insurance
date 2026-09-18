package com.insurance.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.security.user.name=test-user",
                "spring.security.user.password=test-pass"
        })
class ConfigServerApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesGatewayConfigurationToAuthenticatedClients() {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("test-user", "test-pass")
                .getForEntity("/api-gateway/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"name\":\"api-gateway\"")
                // service specific file ...
                .contains("spring.cloud.gateway.server.webflux.routes[0].id")
                .contains("gateway.security.public-paths[0]")
                // ... merged with the shared application.yml
                .contains("eureka.client.service-url.defaultZone");
    }

    @Test
    void rejectsAnonymousAccessToConfiguration() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-gateway/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
