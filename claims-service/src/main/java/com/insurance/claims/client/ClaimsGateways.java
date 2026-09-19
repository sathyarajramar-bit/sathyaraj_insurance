package com.insurance.claims.client;

import com.insurance.common.dto.ApiErrorResponse;
import com.insurance.common.exception.PolicyException;
import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Resilient facades. The coverage check returns 422 with the platform error body when the policy does
 * not cover the incident; that message is surfaced verbatim to the claimant (it explains why).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimsGateways {

    private final PolicyClient policyClient;
    private final DocumentClient documentClient;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "policy-service", fallbackMethod = "policyUnavailable")
    @Retry(name = "policy-service")
    public PolicyClient.PolicyView coverageCheck(String policyNumber, LocalDate incidentDate) {
        try {
            return policyClient.coverageCheck(policyNumber, incidentDate);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Policy " + policyNumber + " not found");
        } catch (FeignException.UnprocessableEntity e) {
            throw new PolicyException(messageOf(e, "Policy " + policyNumber + " does not cover the incident date"));
        }
    }

    @CircuitBreaker(name = "document-service", fallbackMethod = "documentUnavailable")
    @Retry(name = "document-service")
    public DocumentClient.DocumentView document(Long id) {
        try {
            return documentClient.get(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Document", id);
        }
    }

    PolicyClient.PolicyView policyUnavailable(String policyNumber, LocalDate incidentDate, Throwable cause) {
        throw unavailable("policy-service", cause);
    }

    DocumentClient.DocumentView documentUnavailable(Long id, Throwable cause) {
        throw unavailable("document-service", cause);
    }

    private String messageOf(FeignException e, String fallback) {
        try {
            ApiErrorResponse body = objectMapper.readValue(e.contentUTF8(), ApiErrorResponse.class);
            return body.message() != null ? body.message() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static RuntimeException unavailable(String backend, Throwable cause) {
        if (cause instanceof ResourceNotFoundException || cause instanceof PolicyException) {
            return (RuntimeException) cause;
        }
        log.error("{} call failed: {}", backend, cause.toString());
        return new ServiceUnavailableException(backend);
    }
}
