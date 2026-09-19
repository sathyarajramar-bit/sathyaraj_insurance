package com.insurance.notification.template;

/** Subject is used by e-mail only; the SMS body is the short text. */
public record RenderedMessage(String subject, String emailBody, String smsBody) {
}
