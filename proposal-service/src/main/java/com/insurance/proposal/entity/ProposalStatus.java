package com.insurance.proposal.entity;

import java.util.Set;

/** DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED | REJECTED (SUBMITTED may also be decided directly). */
public enum ProposalStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED;

    public boolean canTransitionTo(ProposalStatus target) {
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> Set.of(UNDER_REVIEW, APPROVED, REJECTED).contains(target);
            case UNDER_REVIEW -> target == APPROVED || target == REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }

    public boolean isFinal() {
        return this == APPROVED || this == REJECTED;
    }
}
