package com.insurance.notification.provider;

import com.insurance.notification.config.NotificationProperties;
import com.insurance.notification.entity.Channel;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class SmtpEmailProviderTest {

    private JavaMailSenderImpl mailSender;
    private NotificationProperties properties;

    @BeforeEach
    void setUp() {
        mailSender = spy(new JavaMailSenderImpl());   // real createMimeMessage(), stubbed send()
        properties = new NotificationProperties();
        properties.getEmail().setProvider("SMTP");
        properties.getEmail().setFrom("noreply@insurance.local");
        properties.getEmail().setFromName("Insurance Platform");
    }

    @Test
    void buildsPlainTextMessageWithFromToSubjectAndBody() throws Exception {
        doNothing().when(mailSender).send(any(MimeMessage.class));
        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender, properties);

        String id = provider.send("jane@example.com", "Policy POL-1 issued", "Hello Jane,\nyour policy is active.");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(provider.channel()).isEqualTo(Channel.EMAIL);
        assertThat(provider.name()).isEqualTo("SMTP");
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("noreply@insurance.local");
        assertThat(((InternetAddress) sent.getFrom()[0]).getPersonal()).isEqualTo("Insurance Platform");
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("jane@example.com");
        assertThat(sent.getSubject()).isEqualTo("Policy POL-1 issued");
        assertThat(sent.getContent().toString()).contains("your policy is active");
        assertThat(sent.getContentType()).startsWith("text/plain");
        assertThat(id).isNotBlank();                    // provider message id is recorded on the notification row
    }

    @Test
    void transportFailurePropagatesSoTheRowIsMarkedFailedAndRetried() {
        doThrow(new MailAuthenticationException("535-5.7.8 Username and Password not accepted"))
                .when(mailSender).send(any(MimeMessage.class));
        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender, properties);

        assertThatThrownBy(() -> provider.send("jane@example.com", "s", "b"))
                .isInstanceOf(MailAuthenticationException.class)
                .hasMessageContaining("535");
    }

    @Test
    void refusesToStartWithoutAFromAddress() {
        properties.getEmail().setFrom(" ");
        assertThatThrownBy(() -> new SmtpEmailProvider(mailSender, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notification.email.from");
    }
}
