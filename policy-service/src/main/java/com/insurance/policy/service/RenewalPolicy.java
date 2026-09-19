package com.insurance.policy.service;

import com.insurance.policy.config.PolicyProperties;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The renewal rule in one place:
 * ACTIVE within {@code renewalWindowDays} of the end date, or EXPIRED within {@code renewalGraceDays}
 * after it. CANCELLED, already renewed, or too old => not eligible (a fresh quote is needed).
 */
@Component
@RequiredArgsConstructor
public class RenewalPolicy {

    private final PolicyProperties properties;

    public record Result(boolean eligible, String message) {
    }

    public Result evaluate(Policy policy) {
        return evaluate(policy, LocalDate.now());
    }

    public Result evaluate(Policy policy, LocalDate today) {
        if (policy.getRenewedByPolicyNumber() != null) {
            return new Result(false, "Policy has already been renewed by " + policy.getRenewedByPolicyNumber());
        }
        if (policy.getStatus() == PolicyStatus.CANCELLED || policy.getStatus() == PolicyStatus.PENDING) {
            return new Result(false, "Policy is " + policy.getStatus());
        }
        LocalDate windowStart = policy.getEndDate().minusDays(properties.getRenewalWindowDays());
        LocalDate graceEnd = policy.getEndDate().plusDays(properties.getRenewalGraceDays());
        if (today.isBefore(windowStart)) {
            return new Result(false, "Renewal opens on " + windowStart + " (" + properties.getRenewalWindowDays() + " days before expiry)");
        }
        if (today.isAfter(graceEnd)) {
            return new Result(false, "Grace period ended on " + graceEnd + "; a new quote is required");
        }
        return new Result(true, "Renewable until " + graceEnd);
    }
}
