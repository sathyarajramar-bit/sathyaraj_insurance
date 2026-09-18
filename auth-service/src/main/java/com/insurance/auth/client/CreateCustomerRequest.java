package com.insurance.auth.client;

/** Payload sent to customer-service {@code POST /api/customers} (contract owned by customer-service). */
public record CreateCustomerRequest(Long userId, String email, String firstName, String lastName, String phone) {
}
