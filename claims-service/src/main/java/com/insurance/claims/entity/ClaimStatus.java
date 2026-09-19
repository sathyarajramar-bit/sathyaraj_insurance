package com.insurance.claims.entity;

import java.util.Set;

/**
 * REGISTERED -> UNDER_REVIEW <-> DOCUMENTS_REQUIRED; UNDER_REVIEW -> APPROVED | REJECTED;
 * APPROVED -> SETTLED -> CLOSED; REJECTED -> CLOSED. CLOSED -> UNDER_REVIEW only through the explicit reopen.
 */
public enum ClaimStatus {
    REGISTERED, UNDER_REVIEW, DOCUMENTS_REQUIRED, APPROVED, REJECTED, SETTLED, CLOSED;

    public boolean canTransitionTo(ClaimStatus target) {
        return switch (this) {
            case REGISTERED -> target == UNDER_REVIEW || target == DOCUMENTS_REQUIRED || target == REJECTED;
            case UNDER_REVIEW -> Set.of(DOCUMENTS_REQUIRED, APPROVED, REJECTED).contains(target);
            case DOCUMENTS_REQUIRED -> target == UNDER_REVIEW || target == REJECTED;
            case APPROVED -> target == SETTLED;
            case REJECTED -> target == CLOSED;
            case SETTLED -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
