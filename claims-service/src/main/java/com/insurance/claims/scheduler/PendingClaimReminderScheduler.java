package com.insurance.claims.scheduler;

import com.insurance.claims.config.ClaimsProperties;
import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.repository.ClaimRepository;
import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Reminds customers whose claim has been waiting in DOCUMENTS_REQUIRED for more than N days. The
 * idempotency key includes the day, so notification-service drops repeats within the same day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingClaimReminderScheduler {

    private final ClaimRepository claimRepository;
    private final ClaimsProperties properties;
    private final NotificationPublisher notifications;

    @Scheduled(cron = "${claims.reminder-cron:0 30 9 * * *}")
    public int remindPendingDocuments() {
        Instant cutoff = Instant.now().minus(properties.getPendingDocumentsReminderDays(), ChronoUnit.DAYS);
        List<Claim> waiting = claimRepository.findByStatusAndDocumentsRequestedAtBefore(ClaimStatus.DOCUMENTS_REQUIRED, cutoff);
        String day = Instant.now().truncatedTo(ChronoUnit.DAYS).toString();
        for (Claim c : waiting) {
            notifications.publish(new NotificationRequest(NotificationEventType.CLAIM_DOCUMENTS_REQUIRED, c.getUserId(), c.getCustomerId(),
                    null, null, "CLAIM", c.getClaimNumber(),
                    Map.of("claimNumber", c.getClaimNumber(), "documentsRequested", c.getDocumentsRequested() == null ? "" : c.getDocumentsRequested(),
                            "reminder", "true"),
                    "CLAIM_DOCUMENTS_REMINDER:" + c.getClaimNumber() + ":" + day));
        }
        if (!waiting.isEmpty()) {
            log.info("Sent {} pending-document reminder(s)", waiting.size());
        }
        return waiting.size();
    }
}
