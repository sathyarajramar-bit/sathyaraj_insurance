package com.insurance.claims.service;

import com.insurance.claims.entity.Claim;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

/** Claimant reads/attaches for own claims; CLAIMS_HANDLER/ADMIN handle every claim; AGENT and SERVICE read. */
@Component
public class ClaimAccessPolicy {

    public static final String[] STAFF = {"ADMIN", "CLAIMS_HANDLER"};

    public void assertCanRead(AuthenticatedUser user, Claim claim) {
        if (!claim.isOwnedBy(user.userId()) && !user.hasAnyRole("ADMIN", "CLAIMS_HANDLER", "AGENT", "SERVICE")) {
            throw new ForbiddenException("You are not allowed to access this claim");
        }
    }

    public void assertOwnerOrStaff(AuthenticatedUser user, Claim claim) {
        if (!claim.isOwnedBy(user.userId()) && !user.hasAnyRole(STAFF)) {
            throw new ForbiddenException("You are not allowed to modify this claim");
        }
    }

    public boolean isStaff(AuthenticatedUser user) {
        return user.hasAnyRole(STAFF);
    }
}
