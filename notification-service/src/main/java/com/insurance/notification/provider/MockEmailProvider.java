package com.insurance.notification.provider;

import com.insurance.notification.entity.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Logs instead of sending. An address at the domain fail.test fails, to exercise the retry path. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.email", name = "provider", havingValue = "MOCK", matchIfMissing = true)
public class MockEmailProvider implements ChannelProvider {

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public String name() {
        return "MOCK_EMAIL";
    }

    @Override
    public String send(String recipient, String subject, String body) {
        if (recipient.endsWith("@fail.test")) {
            throw new IllegalStateException("SMTP connection refused");
        }
        String id = "email_" + UUID.randomUUID();
        log.info("MOCK EMAIL to {} | {} | {}", recipient, subject, body.replace('\n', ' '));
        return id;
    }
}
