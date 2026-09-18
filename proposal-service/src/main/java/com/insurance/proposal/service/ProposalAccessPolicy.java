package com.insurance.proposal.service;

import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.proposal.entity.Proposal;
import org.springframework.stereotype.Component;

/** Owner edits/submits; ADMIN/AGENT review and read all; SERVICE (payment/policy) reads. */
@Component
public class ProposalAccessPolicy {

    public void assertCanRead(AuthenticatedUser user, Proposal proposal) {
        if (!(proposal.isOwnedBy(user.userId()) || user.hasAnyRole("ADMIN", "AGENT", "SERVICE"))) {
            throw new ForbiddenException("You are not allowed to access this proposal");
        }
    }

    public void assertOwner(AuthenticatedUser user, Proposal proposal) {
        if (!(proposal.isOwnedBy(user.userId()) || user.hasAnyRole("ADMIN", "AGENT"))) {
            throw new ForbiddenException("You are not allowed to modify this proposal");
        }
    }
}
