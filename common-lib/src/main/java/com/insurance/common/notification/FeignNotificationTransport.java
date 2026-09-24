package com.insurance.common.notification;

/** Synchronous HTTP delivery: {@code POST /api/notifications} on notification-service through Eureka + Feign. */
public class FeignNotificationTransport implements NotificationTransport {

    private final NotificationClient client;

    public FeignNotificationTransport(NotificationClient client) {
        this.client = client;
    }

    @Override
    public void send(NotificationRequest request) {
        client.send(request);
    }
}
