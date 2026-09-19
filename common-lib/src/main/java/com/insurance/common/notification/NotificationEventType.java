package com.insurance.common.notification;

/** Every business moment the platform notifies customers about. notification-service maps each to templates and channels. */
public enum NotificationEventType {
    REGISTRATION, QUOTE_GENERATED, QUOTE_EXPIRED,
    PROPOSAL_SUBMITTED, PROPOSAL_APPROVED, PROPOSAL_REJECTED,
    PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REFUNDED,
    POLICY_ISSUED, POLICY_RENEWAL_REMINDER, POLICY_EXPIRED, POLICY_CANCELLED,
    CLAIM_REGISTERED, CLAIM_DOCUMENTS_REQUIRED, CLAIM_APPROVED, CLAIM_REJECTED, CLAIM_SETTLED
}
