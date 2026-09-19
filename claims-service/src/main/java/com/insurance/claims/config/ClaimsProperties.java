package com.insurance.claims.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "claims")
public class ClaimsProperties {

    /** A claim must be registered within this many days of the incident. */
    private int maxDaysAfterIncident = 30;

    /** Remind customers whose claim has waited in DOCUMENTS_REQUIRED for this long. */
    private int pendingDocumentsReminderDays = 7;

    private String reminderCron = "0 30 9 * * *";
}
