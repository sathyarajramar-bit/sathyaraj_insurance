package com.insurance.notification.scheduler;

import com.insurance.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-attempts FAILED deliveries (provider outage) up to notification.retry.max-attempts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "${notification.retry.cron:0 */5 * * * *}")
    public int retryFailed() {
        int retried = notificationService.retryFailed();
        if (retried > 0) {
            log.info("Retried {} failed notification(s)", retried);
        }
        return retried;
    }
}
