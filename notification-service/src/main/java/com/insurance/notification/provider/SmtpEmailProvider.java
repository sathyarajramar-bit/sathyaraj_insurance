package com.insurance.notification.provider;

import com.insurance.notification.config.NotificationProperties;
import com.insurance.notification.entity.Channel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sends through any SMTP relay configured under {@code spring.mail.*}; the defaults in the config-server
 * target Gmail (smtp.gmail.com:587, STARTTLS, App Password). Selected with
 * {@code notification.email.provider=SMTP}. Failures propagate as exceptions so NotificationService marks the
 * row FAILED and the retry job picks it up; nothing is swallowed here.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.email", name = "provider", havingValue = "SMTP")
public class SmtpEmailProvider implements ChannelProvider {

    private final JavaMailSender mailSender;
    private final NotificationProperties.Email settings;

    public SmtpEmailProvider(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.settings = properties.getEmail();
        if (settings.getFrom() == null || settings.getFrom().isBlank()) {
            throw new IllegalStateException("notification.email.from is required when notification.email.provider=SMTP");
        }
        log.info("SMTP e-mail provider active, sending as {} <{}>", settings.getFromName(), settings.getFrom());
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public String name() {
        return "SMTP";
    }

    @Override
    public String send(String recipient, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(settings.getFrom(), settings.getFromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);          // templates render plain text
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not build e-mail for " + recipient + ": " + e.getMessage(), e);
        }
        mailSender.send(message);                 // MailException (auth, connect, rejected recipient) propagates
        String messageId = messageId(message);
        log.info("SMTP e-mail sent to {} | {} | id {}", recipient, subject, messageId);
        return messageId;
    }

    /** JavaMailSenderImpl assigns the Message-ID during send; fall back to a local id if the transport did not. */
    private static String messageId(MimeMessage message) {
        try {
            String id = message.getMessageID();
            return id != null ? id : "smtp_" + java.util.UUID.randomUUID();
        } catch (MessagingException e) {
            return "smtp_" + java.util.UUID.randomUUID();
        }
    }
}
