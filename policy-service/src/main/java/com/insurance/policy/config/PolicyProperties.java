package com.insurance.policy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** {@code policy.*}: renewal window, grace period and job schedules. */
@Getter
@Setter
@ConfigurationProperties(prefix = "policy")
public class PolicyProperties {

    /** Renewal may be paid this many days BEFORE the end date. */
    private int renewalWindowDays = 30;

    /** An EXPIRED policy may still be renewed this many days AFTER the end date; beyond that a new quote is needed. */
    private int renewalGraceDays = 30;

    /** Reminders are sent this many days before end date (each once). */
    private List<Integer> reminderDaysBefore = List.of(30, 15, 7);

    private Jobs jobs = new Jobs();

    @Getter
    @Setter
    public static class Jobs {
        private String expiryCron = "0 5 0 * * *";      // 00:05 daily
        private String reminderCron = "0 15 8 * * *";   // 08:15 daily
    }
}
