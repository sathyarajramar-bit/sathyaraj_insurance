package com.insurance.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Single entry point for all clients (web, mobile, partners).
 *
 * <p>Responsibilities (and only these; business logic never lives here):
 * <ul>
 *   <li>Route {@code /api/<resource>/**} to the owning microservice, discovered via Eureka.</li>
 *   <li>Authenticate requests once by validating the JWT and forwarding the identity as headers.</li>
 *   <li>Attach a correlation id to every request for log tracing across services.</li>
 *   <li>Log every request/response with status and latency.</li>
 *   <li>Translate gateway level failures (no route, service down, timeout) into the platform's
 *       standard JSON error format.</li>
 * </ul>
 *
 * <p>Interview notes:
 * <ul>
 *   <li><b>Why a gateway?</b> Clients see one host, one TLS certificate and one auth scheme.
 *       Cross-cutting concerns (auth, CORS, rate limiting, logging) are implemented once.</li>
 *   <li><b>Why WebFlux?</b> A gateway is I/O bound: it holds many open connections doing little CPU work.
 *       Netty's event loop handles that with a handful of threads instead of one thread per request.</li>
 *   <li><b>Alternatives:</b> Nginx/Kong/Envoy (no Java, but no Spring integration), Zuul 1 (blocking, EOL).</li>
 *   <li><b>Failure modes:</b> Eureka down -> gateway keeps using its cached registry; a service down ->
 *       503 from {@link com.insurance.gateway.exception.GatewayExceptionHandler}; Config Server down at
 *       startup -> retries, then starts without routes (or refuses to start when fail-fast is on).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
