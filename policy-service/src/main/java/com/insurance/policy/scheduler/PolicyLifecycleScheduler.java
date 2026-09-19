package com.insurance.policy.scheduler;

import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.policy.config.PolicyProperties;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import com.insurance.policy.entity.RenewalReminder;
import com.insurance.policy.repository.PolicyRepository;
import com.insurance.policy.repository.RenewalReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Two daily jobs, both idempotent so they are safe on several instances:
 * expiry (ACTIVE past end date -> EXPIRED with one UPDATE, plus a POLICY_EXPIRED notification each) and
 * renewal reminders (for each configured "days before", notify policies ending that day and record the
 * (policy, days) pair so a rerun never sends twice).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyLifecycleScheduler {

    private final PolicyRepository policyRepository;
    private final RenewalReminderRepository reminderRepository;
    private final PolicyProperties properties;
    private final NotificationPublisher notifications;

    @Scheduled(cron = "${policy.jobs.expiry-cron:0 5 0 * * *}")
    @Transactional
    public int expirePolicies() {
        LocalDate today = LocalDate.now();
        List<Policy> expiring = policyRepository.findByStatusAndEndDateBefore(PolicyStatus.ACTIVE, today);
        int expired = policyRepository.expireOverdue(today, Instant.now(), PolicyStatus.ACTIVE, PolicyStatus.EXPIRED);
        expiring.forEach(p -> notifications.publish(NotificationRequest.of(NotificationEventType.POLICY_EXPIRED, p.getUserId(), p.getCustomerId(),
                p.getHolderEmail(), null, "POLICY", p.getPolicyNumber(),
                Map.of("policyNumber", p.getPolicyNumber(), "endDate", p.getEndDate().toString(),
                        "graceEnd", p.getEndDate().plusDays(properties.getRenewalGraceDays()).toString()))));
        if (expired > 0) {
            log.info("Expired {} policy(ies)", expired);
        }
        return expired;
    }

    @Scheduled(cron = "${policy.jobs.reminder-cron:0 15 8 * * *}")
    @Transactional
    public int sendRenewalReminders() {
        int sent = 0;
        LocalDate today = LocalDate.now();
        for (int days : properties.getReminderDaysBefore()) {
            for (Policy p : policyRepository.findByStatusAndEndDate(PolicyStatus.ACTIVE, today.plusDays(days))) {
                if (p.getRenewedByPolicyNumber() != null || reminderRepository.existsByPolicyNumberAndReminderDays(p.getPolicyNumber(), days)) {
                    continue;
                }
                notifications.publish(new NotificationRequest(NotificationEventType.POLICY_RENEWAL_REMINDER, p.getUserId(), p.getCustomerId(),
                        p.getHolderEmail(), null, "POLICY", p.getPolicyNumber(),
                        Map.of("policyNumber", p.getPolicyNumber(), "endDate", p.getEndDate().toString(), "daysLeft", String.valueOf(days),
                                "premium", p.getPremiumAmount().toPlainString()),
                        "POLICY_RENEWAL_REMINDER:" + p.getPolicyNumber() + ":" + days));
                reminderRepository.save(RenewalReminder.builder().policyNumber(p.getPolicyNumber()).reminderDays(days).sentAt(Instant.now()).build());
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Sent {} renewal reminder(s)", sent);
        }
        return sent;
    }
}
