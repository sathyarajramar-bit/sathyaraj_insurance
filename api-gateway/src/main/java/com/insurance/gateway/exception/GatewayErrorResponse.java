package com.insurance.gateway.exception;

import java.time.Instant;

/**
 * The platform-wide error body. Every service's {@code @RestControllerAdvice} produces the same shape,
 * so clients need one error parser regardless of where the failure happened.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-17T10:15:30Z",
 *   "status": 401,
 *   "error": "UNAUTHORIZED",
 *   "message": "Token has expired",
 *   "path": "/api/policies"
 * }
 * </pre>
 */
public record GatewayErrorResponse(Instant timestamp, int status, String error, String message, String path) {

    public static GatewayErrorResponse of(int status, String error, String message, String path) {
        return new GatewayErrorResponse(Instant.now(), status, error, message, path);
    }
}
