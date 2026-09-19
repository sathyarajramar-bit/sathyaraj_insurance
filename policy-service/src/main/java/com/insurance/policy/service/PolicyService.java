package com.insurance.policy.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.PolicyException;
import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.policy.dto.PolicyResponse;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import com.insurance.policy.exception.PolicyNotFoundException;
import com.insurance.policy.mapper.PolicyMapper;
import com.insurance.policy.repository.PolicyRepository;
import com.insurance.policy.repository.PolicySpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Reads and administration. Customers see only their own policies; ADMIN/AGENT/CLAIMS_HANDLER/SERVICE see all. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyMapper mapper;
    private final RenewalPolicy renewalPolicy;
    private final NotificationPublisher notifications;

    @Transactional(readOnly = true)
    public PolicyResponse getByNumber(String policyNumber) {
        Policy policy = find(policyNumber);
        assertCanRead(CurrentUser.require(), policy);
        return mapper.toResponse(policy, renewalPolicy);
    }

    @Transactional(readOnly = true)
    public PageResponse<PolicyResponse> search(Long customerId, PolicyStatus status, String registrationNumber, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        Long userFilter = user.hasAnyRole("ADMIN", "AGENT", "CLAIMS_HANDLER", "SERVICE") ? null : user.userId();
        if (userFilter != null && customerId != null) {
            throw new ForbiddenException("Customers can only list their own policies");
        }
        return PageResponse.from(policyRepository.findAll(
                PolicySpecifications.withFilters(userFilter, customerId, status, registrationNumber), pageable), p -> mapper.toResponse(p, renewalPolicy));
    }

    /** Free-look / customer initiated cancellation. Refund (if any) is an ADMIN action in payment-service. */
    @Transactional
    public PolicyResponse cancel(String policyNumber, String reason) {
        AuthenticatedUser user = CurrentUser.require();
        Policy policy = find(policyNumber);
        if (!policy.isOwnedBy(user.userId()) && !user.hasAnyRole("ADMIN", "AGENT")) {
            throw new ForbiddenException("You are not allowed to cancel this policy");
        }
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new PolicyException("Only ACTIVE policies can be cancelled (status " + policy.getStatus() + ")");
        }
        policy.setStatus(PolicyStatus.CANCELLED);
        policy.setCancelledAt(Instant.now());
        policy.setCancellationReason(reason);
        log.info("Policy {} cancelled by user {}: {}", policyNumber, user.userId(), reason);
        notifications.publish(NotificationRequest.of(NotificationEventType.POLICY_CANCELLED, policy.getUserId(), policy.getCustomerId(),
                policy.getHolderEmail(), null, "POLICY", policyNumber, Map.of("policyNumber", policyNumber, "reason", reason)));
        return mapper.toResponse(policy, renewalPolicy);
    }

    /** Called by claims-service: is this policy in force on the incident date and does it belong to this customer? */
    @Transactional(readOnly = true)
    public PolicyResponse getForClaim(String policyNumber, LocalDate incidentDate) {
        Policy policy = find(policyNumber);
        assertCanRead(CurrentUser.require(), policy);
        if (!policy.coversOn(incidentDate)) {
            throw new PolicyException("Policy " + policyNumber + " does not cover " + incidentDate + " (status " + policy.getStatus()
                    + ", period " + policy.getStartDate() + " to " + policy.getEndDate() + ")");
        }
        return mapper.toResponse(policy, renewalPolicy);
    }

    Policy find(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber).orElseThrow(() -> new PolicyNotFoundException(policyNumber));
    }

    static void assertCanRead(AuthenticatedUser user, Policy policy) {
        if (!policy.isOwnedBy(user.userId()) && !user.hasAnyRole("ADMIN", "AGENT", "CLAIMS_HANDLER", "SERVICE")) {
            throw new ForbiddenException("You are not allowed to access this policy");
        }
    }
}
