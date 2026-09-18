package com.insurance.quote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code quote.*}: business and job settings, all overridable per environment. */
@Getter
@Setter
@ConfigurationProperties(prefix = "quote")
public class QuoteProperties {

    /** How long a generated quote can be accepted. */
    private Duration validity = Duration.ofDays(15);

    private Duration cacheTtl = Duration.ofMinutes(15);

    private Expiry expiry = new Expiry();

    @Getter
    @Setter
    public static class Expiry {
        /** Spring cron; default every 10 minutes; "-" disables the job. */
        private String cron = "0 */10 * * * *";
    }
}
