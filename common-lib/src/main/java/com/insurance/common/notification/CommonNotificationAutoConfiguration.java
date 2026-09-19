package com.insurance.common.notification;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Registers the notification Feign client and publisher in every service that has OpenFeign, unless
 * {@code notifications.enabled=false} (tests, services that never notify).
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@ConditionalOnProperty(prefix = "notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(clients = NotificationClient.class)
@EnableAsync
public class CommonNotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    NotificationPublisher notificationPublisher(NotificationClient client) {
        return new NotificationPublisher(client);
    }
}
