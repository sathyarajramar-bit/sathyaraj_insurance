package com.insurance.proposal.event;

import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.proposal.entity.ProposalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** After-commit consumer: logs the event and forwards the matching notification. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProposalEventLogger {

    private final NotificationPublisher notifications;

    @Async
    @TransactionalEventListener
    public void on(ProposalSubmittedEvent event) {
        log.info("EVENT ProposalSubmitted proposal={} customer={} product={} premium={}",
                event.proposalNumber(), event.customerId(), event.productCode(), event.premiumAmount());
        notifications.publish(NotificationRequest.of(NotificationEventType.PROPOSAL_SUBMITTED, event.userId(), event.customerId(), null, null,
                "PROPOSAL", event.proposalNumber(), Map.of("proposalNumber", event.proposalNumber())));
    }

    @Async
    @TransactionalEventListener
    public void on(ProposalDecidedEvent event) {
        log.info("EVENT ProposalDecided proposal={} decision={} customer={} reason={}",
                event.proposalNumber(), event.decision(), event.customerId(), event.reason());
        boolean approved = event.decision() == ProposalStatus.APPROVED;
        notifications.publish(NotificationRequest.of(approved ? NotificationEventType.PROPOSAL_APPROVED : NotificationEventType.PROPOSAL_REJECTED,
                event.userId(), event.customerId(), null, null, "PROPOSAL", event.proposalNumber(),
                Map.of("proposalNumber", event.proposalNumber(), "premiumAmount", event.premiumAmount().toPlainString(),
                        "reason", event.reason() == null ? "" : event.reason())));
    }
}
