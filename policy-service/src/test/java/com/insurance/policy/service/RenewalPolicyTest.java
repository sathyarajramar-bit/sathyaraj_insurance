package com.insurance.policy.service;

import com.insurance.policy.config.PolicyProperties;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalPolicyTest {

    private final RenewalPolicy rule = new RenewalPolicy(new PolicyProperties());
    private final LocalDate end = LocalDate.of(2026, 12, 31);

    private Policy policy(PolicyStatus status) {
        return Policy.builder().policyNumber("PL-1").status(status).startDate(end.minusYears(1).plusDays(1)).endDate(end).build();
    }

    @Test
    void activePolicyRenewableOnlyInsideTheWindow() {
        assertThat(rule.evaluate(policy(PolicyStatus.ACTIVE), end.minusDays(31)).eligible()).isFalse();
        assertThat(rule.evaluate(policy(PolicyStatus.ACTIVE), end.minusDays(30)).eligible()).isTrue();
        assertThat(rule.evaluate(policy(PolicyStatus.ACTIVE), end).eligible()).isTrue();
    }

    @Test
    void expiredPolicyRenewableOnlyWithinGrace() {
        assertThat(rule.evaluate(policy(PolicyStatus.EXPIRED), end.plusDays(30)).eligible()).isTrue();
        RenewalPolicy.Result late = rule.evaluate(policy(PolicyStatus.EXPIRED), end.plusDays(31));
        assertThat(late.eligible()).isFalse();
        assertThat(late.message()).contains("new quote is required");
    }

    @Test
    void cancelledOrAlreadyRenewedIsNotEligible() {
        assertThat(rule.evaluate(policy(PolicyStatus.CANCELLED), end).eligible()).isFalse();
        Policy renewed = policy(PolicyStatus.ACTIVE);
        renewed.setRenewedByPolicyNumber("PL-2");
        assertThat(rule.evaluate(renewed, end).message()).contains("already been renewed");
    }
}
