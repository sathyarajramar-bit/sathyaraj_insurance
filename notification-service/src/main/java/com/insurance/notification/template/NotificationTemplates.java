package com.insurance.notification.template;

import com.insurance.common.notification.NotificationEventType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * One template per event type. Kept in code (not a template engine) so messages are versioned and
 * reviewed like any other logic; a Thymeleaf/FreeMarker renderer can replace this class transparently.
 */
@Component
public class NotificationTemplates {

    public RenderedMessage render(NotificationEventType type, Map<String, String> p) {
        return switch (type) {
            case REGISTRATION -> msg("Welcome to Insurance Platform",
                    "Hi " + v(p, "firstName") + ", your account is ready. Browse motor insurance products and get a quote in minutes.",
                    "Welcome " + v(p, "firstName") + "! Your Insurance Platform account is ready.");
            case QUOTE_GENERATED -> msg("Your quote " + v(p, "quoteNumber"),
                    "Quote " + v(p, "quoteNumber") + " for " + v(p, "product") + ": premium " + v(p, "finalPremium") + ", valid until " + v(p, "validUntil") + ". Accept it to continue to a proposal.",
                    "Quote " + v(p, "quoteNumber") + ": premium " + v(p, "finalPremium") + ", valid till " + v(p, "validUntil") + ".");
            case QUOTE_EXPIRED -> msg("Quote " + v(p, "quoteNumber") + " has expired",
                    "Quote " + v(p, "quoteNumber") + " has expired. Generate a new quote to get updated pricing.",
                    "Quote " + v(p, "quoteNumber") + " expired. Get a new quote anytime.");
            case PROPOSAL_SUBMITTED -> msg("Proposal " + v(p, "proposalNumber") + " received",
                    "We received proposal " + v(p, "proposalNumber") + ". Our team will review it shortly.",
                    "Proposal " + v(p, "proposalNumber") + " received; under review.");
            case PROPOSAL_APPROVED -> msg("Proposal " + v(p, "proposalNumber") + " approved",
                    "Proposal " + v(p, "proposalNumber") + " is approved. Pay the premium of " + v(p, "premiumAmount") + " to get your policy issued.",
                    "Proposal " + v(p, "proposalNumber") + " approved. Pay " + v(p, "premiumAmount") + " to issue your policy.");
            case PROPOSAL_REJECTED -> msg("Proposal " + v(p, "proposalNumber") + " not approved",
                    "Proposal " + v(p, "proposalNumber") + " could not be approved: " + v(p, "reason"),
                    "Proposal " + v(p, "proposalNumber") + " not approved: " + v(p, "reason"));
            case PAYMENT_SUCCESS -> msg("Payment received",
                    "We received your payment of " + v(p, "currency") + " " + v(p, "amount") + " (ref " + v(p, "reference") + "). Your policy is being issued.",
                    "Payment of " + v(p, "amount") + " received. Policy being issued.");
            case PAYMENT_FAILED -> msg("Payment failed",
                    "Your payment of " + v(p, "amount") + " for " + v(p, "reference") + " failed: " + v(p, "reason") + ". Please try again.",
                    "Payment of " + v(p, "amount") + " failed: " + v(p, "reason"));
            case PAYMENT_REFUNDED -> msg("Refund processed",
                    "A refund of " + v(p, "amount") + " has been processed: " + v(p, "reason"),
                    "Refund of " + v(p, "amount") + " processed.");
            case POLICY_ISSUED -> msg("Policy " + v(p, "policyNumber") + " issued",
                    "Your " + v(p, "product") + " policy " + v(p, "policyNumber") + " is active from " + v(p, "startDate") + " to " + v(p, "endDate")
                            + ". Premium paid: " + v(p, "premium") + ". The policy schedule is available in your documents.",
                    "Policy " + v(p, "policyNumber") + " active " + v(p, "startDate") + " to " + v(p, "endDate") + ".");
            case POLICY_RENEWAL_REMINDER -> msg("Policy " + v(p, "policyNumber") + " expires in " + v(p, "daysLeft") + " days",
                    "Policy " + v(p, "policyNumber") + " expires on " + v(p, "endDate") + ". Renew now for " + v(p, "premium") + " to stay covered without a break.",
                    "Policy " + v(p, "policyNumber") + " expires " + v(p, "endDate") + " (" + v(p, "daysLeft") + " days). Renew now.");
            case POLICY_EXPIRED -> msg("Policy " + v(p, "policyNumber") + " has expired",
                    "Policy " + v(p, "policyNumber") + " expired on " + v(p, "endDate") + ". You can still renew until " + v(p, "graceEnd") + ".",
                    "Policy " + v(p, "policyNumber") + " expired. Renew by " + v(p, "graceEnd") + ".");
            case POLICY_CANCELLED -> msg("Policy " + v(p, "policyNumber") + " cancelled",
                    "Policy " + v(p, "policyNumber") + " has been cancelled: " + v(p, "reason"),
                    "Policy " + v(p, "policyNumber") + " cancelled.");
            case CLAIM_REGISTERED -> msg("Claim " + v(p, "claimNumber") + " registered",
                    "Claim " + v(p, "claimNumber") + " on policy " + v(p, "policyNumber") + " is registered (claimed " + v(p, "claimedAmount") + "). A handler will review it.",
                    "Claim " + v(p, "claimNumber") + " registered.");
            case CLAIM_DOCUMENTS_REQUIRED -> msg("Documents needed for claim " + v(p, "claimNumber"),
                    "To proceed with claim " + v(p, "claimNumber") + " please upload: " + v(p, "documentsRequested"),
                    "Claim " + v(p, "claimNumber") + " needs documents: " + v(p, "documentsRequested"));
            case CLAIM_APPROVED -> msg("Claim " + v(p, "claimNumber") + " approved",
                    "Claim " + v(p, "claimNumber") + " is approved for " + v(p, "approvedAmount") + ". Settlement will follow.",
                    "Claim " + v(p, "claimNumber") + " approved for " + v(p, "approvedAmount") + ".");
            case CLAIM_REJECTED -> msg("Claim " + v(p, "claimNumber") + " rejected",
                    "Claim " + v(p, "claimNumber") + " was rejected: " + v(p, "reason"),
                    "Claim " + v(p, "claimNumber") + " rejected: " + v(p, "reason"));
            case CLAIM_SETTLED -> msg("Claim " + v(p, "claimNumber") + " settled",
                    "Claim " + v(p, "claimNumber") + " has been settled for " + v(p, "settledAmount") + " (ref " + v(p, "settlementReference") + ").",
                    "Claim " + v(p, "claimNumber") + " settled: " + v(p, "settledAmount") + " paid.");
        };
    }

    private static RenderedMessage msg(String subject, String email, String sms) {
        return new RenderedMessage(subject, email, sms);
    }

    private static String v(Map<String, String> params, String key) {
        String value = params == null ? null : params.get(key);
        return value == null ? "-" : value;
    }
}
