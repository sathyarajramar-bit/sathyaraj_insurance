package com.insurance.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Guarantees every request carries an {@code X-Correlation-Id}.
 *
 * <p>The id is reused if the client sent one (mobile apps often do), generated otherwise, forwarded to
 * downstream services and echoed in the response so a user-reported failure can be found in the logs
 * of every service it touched.
 *
 * <p>Implemented as a {@link WebFilter} (not a gateway {@code GlobalFilter}) so it also covers requests
 * that match no route (404s) and the gateway's own actuator endpoints.
 *
 * <p>Interview note: this is the poor man's distributed tracing. Micrometer Tracing + OpenTelemetry
 * replaces it with W3C {@code traceparent} propagation, spans and a UI (Zipkin/Tempo/Jaeger).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    public static final String CORRELATION_ID_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.CORRELATION_ID);
        String correlationId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

        // Set (not add) just before the response is committed: downstream services may echo the header
        // back and the proxied response headers would otherwise contain it twice.
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(GatewayHeaders.CORRELATION_ID, correlationId);
            return Mono.empty();
        });

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(GatewayHeaders.CORRELATION_ID, correlationId))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
