package com.insurance.proposal.client;

import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Resilient facade over {@link QuoteClient}: idempotent read, so retry + circuit breaker, 503 fallback. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteGateway {

    static final String BACKEND = "quote-service";

    private final QuoteClient quoteClient;

    @CircuitBreaker(name = BACKEND, fallbackMethod = "quoteUnavailable")
    @Retry(name = BACKEND)
    public QuoteClient.QuoteView getQuote(String quoteNumber) {
        try {
            return quoteClient.getQuote(quoteNumber);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Quote " + quoteNumber + " not found");
        }
    }

    QuoteClient.QuoteView quoteUnavailable(String quoteNumber, Throwable cause) {
        if (cause instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        log.error("{} call failed: {}", BACKEND, cause.toString());
        throw new ServiceUnavailableException(BACKEND);
    }
}
