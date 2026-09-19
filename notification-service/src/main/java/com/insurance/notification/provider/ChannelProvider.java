package com.insurance.notification.provider;

import com.insurance.notification.entity.Channel;

/**
 * Port for a delivery channel. Mock implementations log the message; production adapters (SES/SendGrid
 * for EMAIL, Twilio/MSG91 for SMS) implement the same contract and are selected by configuration.
 */
public interface ChannelProvider {

    Channel channel();

    String name();

    /** @return provider message id; throws on delivery failure */
    String send(String recipient, String subject, String body);
}
