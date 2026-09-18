package com.insurance.quote.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Placeholder consumer until notification-service exists (Phase 3H). Runs off the request thread. */
@Slf4j
@Component
public class QuoteEventLogger {

    @Async
    @EventListener
    public void on(QuoteGeneratedEvent event) {
        log.info("EVENT QuoteGenerated quote={} customer={} product={} premium={} validUntil={}",
                event.quoteNumber(), event.customerId(), event.productCode(), event.finalPremium(), event.validUntil());
    }
}
