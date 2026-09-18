package com.insurance.quote.event;

import com.insurance.quote.dto.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Event boundary of this service. Today events are published in-process (Spring ApplicationEvent) and
 * consumed by {@link QuoteEventLogger}; Phase 3H replaces the consumer with a call to
 * notification-service, and a Kafka producer can replace this publisher without touching QuoteService.
 *
 * <p>Why async here: a notification is not part of the "generate quote" contract. The customer must
 * get a quote even if e-mail is down, so the side effect must not run in the request path.
 */
@Component
@RequiredArgsConstructor
public class QuoteEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void quoteGenerated(QuoteResponse quote) {
        publisher.publishEvent(new QuoteGeneratedEvent(quote.quoteNumber(), quote.userId(), quote.customerId(),
                quote.productCode(), quote.finalPremium(), quote.validUntil(), Instant.now()));
    }
}
