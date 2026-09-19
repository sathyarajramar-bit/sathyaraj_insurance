package com.insurance.notification.client;

import com.insurance.common.notification.NotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Uses the email/phone in the request when present; otherwise asks customer-service (by customer id, then user id). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipientResolver {

    private final CustomerClient customerClient;

    public record Recipient(String email, String phone, String firstName) {
    }

    public Recipient resolve(NotificationRequest request) {
        if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            return new Recipient(request.recipientEmail(), request.recipientPhone(), request.params() == null ? null : request.params().get("firstName"));
        }
        CustomerClient.CustomerContact contact = lookup(request.customerId(), request.userId());
        if (contact == null) {
            return new Recipient(null, request.recipientPhone(), null);
        }
        return new Recipient(contact.email(), request.recipientPhone() != null ? request.recipientPhone() : contact.phone(), contact.firstName());
    }

    @CircuitBreaker(name = "customer-service", fallbackMethod = "unavailable")
    @Retry(name = "customer-service")
    CustomerClient.CustomerContact lookup(Long customerId, Long userId) {
        if (customerId != null) {
            return customerClient.getById(customerId);
        }
        return userId != null ? customerClient.getByUserId(userId) : null;
    }

    CustomerClient.CustomerContact unavailable(Long customerId, Long userId, Throwable cause) {
        log.warn("customer-service unavailable while resolving recipient (customer {}, user {}): {}", customerId, userId, cause.toString());
        return null;   // the message is stored as FAILED and retried later
    }
}
