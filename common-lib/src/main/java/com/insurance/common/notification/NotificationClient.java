package com.insurance.common.notification;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Feign contract to notification-service (SERVICE token added by CommonFeignAutoConfiguration). */
@FeignClient(name = "notification-service", path = "/api/notifications", contextId = "platformNotificationClient")
public interface NotificationClient {

    @PostMapping
    void send(@RequestBody NotificationRequest request);
}
