package com.insurance.proposal.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Placeholder consumer until notification-service (3H). Fires only after the publishing transaction committed. */
@Slf4j
@Component
public class ProposalEventLogger {

    @Async
    @TransactionalEventListener
    public void on(ProposalSubmittedEvent event) {
        log.info("EVENT ProposalSubmitted proposal={} customer={} product={} premium={}",
                event.proposalNumber(), event.customerId(), event.productCode(), event.premiumAmount());
    }

    @Async
    @TransactionalEventListener
    public void on(ProposalDecidedEvent event) {
        log.info("EVENT ProposalDecided proposal={} decision={} customer={} reason={}",
                event.proposalNumber(), event.decision(), event.customerId(), event.reason());
    }
}
