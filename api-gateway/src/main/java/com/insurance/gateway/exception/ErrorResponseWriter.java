package com.insurance.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Serialises a {@link GatewayErrorResponse} straight onto the reactive response.
 *
 * <p>Used both by filters that short-circuit a request (401 from the JWT filter) and by the global
 * exception handler, so every error the gateway itself produces has the same JSON shape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            // Headers already sent (e.g. downstream started streaming): we can only close the response.
            return response.setComplete();
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        GatewayErrorResponse body = GatewayErrorResponse.of(status.value(), error, message, path);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(toJson(body));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] toJson(GatewayErrorResponse body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise error response", e);
            return ("{\"status\":" + body.status() + ",\"error\":\"" + body.error() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
