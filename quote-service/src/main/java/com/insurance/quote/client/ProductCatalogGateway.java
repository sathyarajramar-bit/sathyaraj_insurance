package com.insurance.quote.client;

import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilient facade over {@link ProductClient}.
 *
 * <p>Why resilience here and not everywhere: every quote needs product-service (3 calls). If it is slow
 * or down, request threads would pile up waiting and quote-service would fail with it. The circuit
 * breaker fails fast once the error rate crosses the threshold, the retry absorbs single transient
 * failures (safe: all three calls are idempotent reads), and the Feign read timeout bounds each attempt.
 * The fallback never invents data: it raises a clear 503 so the customer can retry.
 *
 * <p>Aspect order (resilience4j default): Retry wraps CircuitBreaker, so every attempt is recorded by
 * the breaker. Business 404s from product-service are translated and are not counted as failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCatalogGateway {

    static final String BACKEND = "product-service";

    private final ProductClient productClient;

    @CircuitBreaker(name = BACKEND, fallbackMethod = "productUnavailable")
    @Retry(name = BACKEND)
    public ProductClient.ProductDetails getProduct(Long productId) {
        try {
            return productClient.getProduct(productId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Product", productId);
        }
    }

    @CircuitBreaker(name = BACKEND, fallbackMethod = "pricingUnavailable")
    @Retry(name = BACKEND)
    public ProductClient.ProductPricing getPricing(Long productId) {
        return productClient.getPricing(productId);
    }

    @CircuitBreaker(name = BACKEND, fallbackMethod = "eligibilityUnavailable")
    @Retry(name = BACKEND)
    public ProductClient.EligibilityResult checkEligibility(Long productId, ProductClient.RiskProfile profile) {
        return productClient.checkEligibility(productId, profile);
    }

    ProductClient.ProductDetails productUnavailable(Long productId, Throwable cause) {
        throw unavailable(cause);
    }

    ProductClient.ProductPricing pricingUnavailable(Long productId, Throwable cause) {
        throw unavailable(cause);
    }

    ProductClient.EligibilityResult eligibilityUnavailable(Long productId, ProductClient.RiskProfile profile, Throwable cause) {
        throw unavailable(cause);
    }

    static RuntimeException unavailable(Throwable cause) {
        if (cause instanceof ResourceNotFoundException notFound) {
            return notFound;
        }
        log.error("{} call failed: {}", BACKEND, cause.toString());
        return new ServiceUnavailableException(BACKEND);
    }
}
