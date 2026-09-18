package com.insurance.quote.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event: a quote was generated. Carries ids and the few facts a consumer (notification-service)
 * needs, never the whole aggregate. The shape is the message contract if/when this moves to Kafka.
 */
public record QuoteGeneratedEvent(String quoteNumber, Long userId, Long customerId, String productCode,
                                  BigDecimal finalPremium, Instant validUntil, Instant occurredAt) {
}
