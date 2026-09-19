package com.insurance.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Provider email = new Provider();
    private Provider sms = new Provider();
    private boolean smsEnabled = true;
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Provider {
        private String provider = "MOCK";
    }

    @Getter
    @Setter
    public static class Retry {
        private String cron = "0 */5 * * * *";
        private int maxAttempts = 5;
    }
}
