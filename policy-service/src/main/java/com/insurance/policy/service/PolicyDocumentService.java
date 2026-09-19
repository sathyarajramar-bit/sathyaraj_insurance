package com.insurance.policy.service;

import com.insurance.policy.client.DocumentClient;
import com.insurance.policy.entity.Policy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Generates the policy schedule and stores it in document-service, asynchronously: issuing the policy
 * must not wait for (or fail because of) document storage. If storage fails the policy stays valid
 * without a schedule id; an admin can regenerate later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyDocumentService {

    private final DocumentClient documentClient;
    private final PolicyScheduleGenerator generator;
    private final PolicyTransactions transactions;

    @Async
    public void attachSchedule(Policy policy) {
        try {
            DocumentClient.DocumentView doc = documentClient.storeGenerated(new DocumentClient.GeneratedDocumentRequest(
                    policy.getUserId(), policy.getCustomerId(), "POLICY", policy.getPolicyNumber(), "POLICY_SCHEDULE",
                    policy.getPolicyNumber() + "-schedule.txt", "text/plain", generator.renderBase64(policy)));
            transactions.linkSchedule(policy.getId(), doc.id());
            log.info("Policy schedule for {} stored as document {}", policy.getPolicyNumber(), doc.id());
        } catch (Exception e) {
            log.error("Could not store policy schedule for {}: {}", policy.getPolicyNumber(), e.toString());
        }
    }
}
