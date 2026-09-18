package com.insurance.quote.entity;

/**
 * DRAFT exists only as the status of an unsaved preview calculation; persisted quotes start at GENERATED.
 * GENERATED -> ACCEPTED (customer proceeds to proposal) | EXPIRED (validity passed) | CANCELLED.
 */
public enum QuoteStatus {
    DRAFT, GENERATED, ACCEPTED, EXPIRED, CANCELLED
}
