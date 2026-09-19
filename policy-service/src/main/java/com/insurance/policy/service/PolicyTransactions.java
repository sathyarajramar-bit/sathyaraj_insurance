package com.insurance.policy.service;

import com.insurance.policy.entity.Policy;
import com.insurance.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional steps of issuance in their own bean so the @Transactional proxy is applied
 * (a self-invocation from PolicyIssuanceService or an @Async method would bypass it).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyTransactions {

    private final PolicyRepository policyRepository;

    @Transactional
    public Policy persist(Policy policy) {
        Policy saved = policyRepository.saveAndFlush(policy);
        if (saved.getRenewedFromPolicyNumber() != null) {
            policyRepository.findByPolicyNumber(saved.getRenewedFromPolicyNumber())
                    .ifPresent(old -> old.setRenewedByPolicyNumber(saved.getPolicyNumber()));
        }
        log.info("Policy {} issued for customer {} ({} to {}) from payment {}", saved.getPolicyNumber(), saved.getCustomerId(),
                saved.getStartDate(), saved.getEndDate(), saved.getPaymentReference());
        return saved;
    }

    @Transactional
    public void linkSchedule(Long policyId, Long documentId) {
        policyRepository.findById(policyId).ifPresent(p -> p.setScheduleDocumentId(documentId));
    }
}
