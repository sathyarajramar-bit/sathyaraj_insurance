package com.insurance.auth.entity;

/** Roles a human user can hold. Service-to-service calls use the pseudo role SERVICE, never stored here. */
public enum Role {
    CUSTOMER, ADMIN, AGENT
}
