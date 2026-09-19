package com.insurance.policy.repository;

import com.insurance.policy.entity.RenewalReminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RenewalReminderRepository extends JpaRepository<RenewalReminder, Long> {

    boolean existsByPolicyNumberAndReminderDays(String policyNumber, int daysBefore);
}
