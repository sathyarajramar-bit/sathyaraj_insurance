package com.insurance.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Email email = new Email();
    private Provider sms = new Provider();
    private boolean smsEnabled = true;
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Provider {
        private String provider = "MOCK";
    }

    /** E-mail specific settings; the SMTP connection itself is standard {@code spring.mail.*}. */
    @Getter
    @Setter
    public static class Email extends Provider {
        /** Sender address; Gmail rewrites it to the authenticated account unless it is a verified alias. */
        private String from;
        private String fromName = "Insurance Platform";
    }

    @Getter
    @Setter
    public static class Retry {
        private String cron = "0 */5 * * * *";
        private int maxAttempts = 5;
    }
}
