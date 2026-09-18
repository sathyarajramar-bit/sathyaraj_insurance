package com.insurance.quote.config;

/**
 * One cache: quote by number. A quote is read many times between generation and payment (customer
 * refreshes, proposal-service, payment-service) but changes only on accept/cancel/expiry, and each of
 * those evicts the entry. TTL is short because a GENERATED quote can silently become EXPIRED.
 * Listings are not cached (per-user, paged, rarely repeated).
 */
public final class CacheNames {

    public static final String QUOTES = "quotes";

    private CacheNames() {
    }
}
