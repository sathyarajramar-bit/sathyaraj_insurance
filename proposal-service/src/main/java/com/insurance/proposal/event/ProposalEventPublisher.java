package com.insurance.proposal.event;

import com.insurance.proposal.dto.ProposalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Event boundary: in-process Spring events today (consumed by {@link ProposalEventLogger}), a Kafka
 * producer later. Listeners run AFTER the transaction commits ({@code @TransactionalEventListener}), so
 * a consumer never sees a proposal state that was rolled back.
 */
@Component
@RequiredArgsConstructor
public class ProposalEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void proposalSubmitted(ProposalResponse p) {
        publisher.publishEvent(new ProposalSubmittedEvent(p.proposalNumber(), p.quoteNumber(), p.userId(), p.customerId(),
                p.productCode(), p.premiumAmount(), Instant.now()));
    }

    public void proposalDecided(ProposalResponse p) {
        publisher.publishEvent(new ProposalDecidedEvent(p.proposalNumber(), p.status(), p.userId(), p.customerId(),
                p.productCode(), p.premiumAmount(), p.decisionReason(), Instant.now()));
    }
}
