package com.insurance.auth.client;

/** Subset of customer-service's response that auth-service cares about. Unknown fields are ignored. */
public record CustomerResponse(Long id, Long userId, String email) {
}
