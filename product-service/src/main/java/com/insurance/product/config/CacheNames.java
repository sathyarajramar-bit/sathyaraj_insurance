package com.insurance.product.config;

/**
 * The three caches of this service and why each exists.
 * <ul>
 *   <li>{@link #PRODUCTS}: full product aggregate by id. Read on every product page and by quote-service for
 *       every quote; changes only when an admin edits the product. Evicted by id on update.</li>
 *   <li>{@link #PRODUCT_CATALOG}: paged listing keyed by filters + page + sort. Hit by every catalogue browse;
 *       any product change evicts the whole cache (cheap: few pages, rare writes).</li>
 *   <li>{@link #PRODUCT_PRICING}: pricing parameters by product id, read by quote-service for every premium
 *       calculation.</li>
 * </ul>
 * Not cached: eligibility checks (input-dependent, computed in microseconds) and admin writes.
 */
public final class CacheNames {

    public static final String PRODUCTS = "products";
    public static final String PRODUCT_CATALOG = "product-catalog";
    public static final String PRODUCT_PRICING = "product-pricing";

    private CacheNames() {
    }
}
