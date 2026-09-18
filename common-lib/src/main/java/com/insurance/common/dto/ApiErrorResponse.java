package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Platform-wide error body, identical to the one the API Gateway produces.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-17T10:15:30Z",
 *   "status": 400,
 *   "error": "VALIDATION_ERROR",
 *   "message": "Vehicle registration number is required",
 *   "path": "/api/quotes",
 *   "fieldErrors": [ { "field": "registrationNumber", "message": "must not be blank" } ]   // optional
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ApiErrorResponse of(int status, String error, String message, String path,
                                      List<FieldErrorDetail> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}
