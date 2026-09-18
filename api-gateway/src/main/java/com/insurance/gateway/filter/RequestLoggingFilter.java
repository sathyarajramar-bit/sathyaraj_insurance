package com.insurance.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * One structured log line per request: method, path, status, latency and correlation id.
 *
 * <p>Runs right after {@link CorrelationIdFilter} so the id is available. Actuator traffic
 * (health probes every few seconds) is logged at DEBUG to keep INFO logs readable.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getPath().pathWithinApplication().value();
        String correlationId = exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);

        return chain.filter(exchange).doFinally(signal -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            long millis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            if (path.startsWith("/actuator")) {
                log.debug("{} {} -> {} ({} ms) correlationId={}", method, path, status, millis, correlationId);
            } else {
                log.info("{} {} -> {} ({} ms) correlationId={}", method, path, status, millis, correlationId);
            }
        });
    }
}
