package com.insurance.notification.provider;

import com.insurance.notification.entity.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.sms", name = "provider", havingValue = "MOCK", matchIfMissing = true)
public class MockSmsProvider implements ChannelProvider {

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public String name() {
        return "MOCK_SMS";
    }

    @Override
    public String send(String recipient, String subject, String body) {
        String id = "sms_" + UUID.randomUUID();
        log.info("MOCK SMS to {} | {}", recipient, body.length() > 160 ? body.substring(0, 157) + "..." : body);
        return id;
    }
}
