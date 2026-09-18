package com.insurance.gateway.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Converts every failure raised inside the gateway into the standard JSON error body.
 *
 * <p>Without this, Spring Boot's default handler answers with its own error format (or an HTML page),
 * which would leak into clients as a second error schema. Typical mappings:
 * <ul>
 *   <li>No route matches the path -> 404 NOT_FOUND</li>
 *   <li>Eureka has no live instance for the service -> 503 SERVICE_UNAVAILABLE</li>
 *   <li>Connection refused / connect timeout -> 503 SERVICE_UNAVAILABLE</li>
 *   <li>Downstream took longer than {@code response-timeout} -> 504 GATEWAY_TIMEOUT</li>
 *   <li>Anything else -> 500 INTERNAL_ERROR (details only in the log, never in the response)</li>
 * </ul>
 *
 * <p>Order -2 places it before Spring Boot's {@code DefaultErrorWebExceptionHandler} (order -1).
 * Note that errors from downstream services are NOT touched: their status and body are proxied as-is.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (ex instanceof NotFoundException) {
            // Raised by the LoadBalancer filter when Eureka knows no instance of the target service.
            log.warn("No instance available for {}: {}", path, ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The service handling this request is currently unavailable. Please retry shortly.");
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return errorResponseWriter.write(exchange, status, "NOT_FOUND", "No route found for " + path);
            }
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            log.warn("Gateway error {} for {}: {}", status.value(), path, rse.getReason());
            return errorResponseWriter.write(exchange, status, status.name(),
                    rse.getReason() != null ? rse.getReason() : status.getReasonPhrase());
        }
        if (ex instanceof ConnectException) {
            log.warn("Could not connect to downstream service for {}: {}", path, ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Could not connect to the service handling this request. Please retry shortly.");
        }
        if (ex instanceof TimeoutException) {
            log.warn("Downstream service timed out for {}", path);
            return errorResponseWriter.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
                    "The service handling this request did not respond in time.");
        }
        log.error("Unexpected gateway error for {}", path, ex);
        return errorResponseWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred at the gateway.");
    }
}
