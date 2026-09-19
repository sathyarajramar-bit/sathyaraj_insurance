package com.insurance.quote.event;

import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Consumer of QuoteGeneratedEvent: logs it and forwards a QUOTE_GENERATED notification (off the request thread). */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteEventLogger {

    private final NotificationPublisher notifications;

    @Async
    @EventListener
    public void on(QuoteGeneratedEvent event) {
        log.info("EVENT QuoteGenerated quote={} customer={} product={} premium={} validUntil={}",
                event.quoteNumber(), event.customerId(), event.productCode(), event.finalPremium(), event.validUntil());
        notifications.publish(NotificationRequest.of(NotificationEventType.QUOTE_GENERATED, event.userId(), event.customerId(), null, null,
                "QUOTE", event.quoteNumber(), Map.of("quoteNumber", event.quoteNumber(), "product", event.productCode(),
                        "finalPremium", event.finalPremium().toPlainString(), "validUntil", event.validUntil().toString().substring(0, 10))));
    }
}
