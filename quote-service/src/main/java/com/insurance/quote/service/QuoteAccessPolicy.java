package com.insurance.quote.service;

import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.quote.entity.Quote;
import org.springframework.stereotype.Component;

/** Customers see their own quotes; ADMIN and AGENT see all; SERVICE (proposal/payment) may read. */
@Component
public class QuoteAccessPolicy {

    public boolean canRead(AuthenticatedUser user, Quote quote) {
        return quote.isOwnedBy(user.userId()) || user.hasAnyRole("ADMIN", "AGENT", "SERVICE");
    }

    public void assertCanRead(AuthenticatedUser user, Quote quote) {
        assertCanRead(user, quote.getUserId());
    }

    public void assertCanRead(AuthenticatedUser user, Long ownerUserId) {
        if (!(ownerUserId.equals(user.userId()) || user.hasAnyRole("ADMIN", "AGENT", "SERVICE"))) {
            throw new ForbiddenException("You are not allowed to access this quote");
        }
    }

    /** Only the owner (or an AGENT acting for them) may change a quote's state. */
    public void assertCanModify(AuthenticatedUser user, Quote quote) {
        if (!(quote.isOwnedBy(user.userId()) || user.hasAnyRole("ADMIN", "AGENT"))) {
            throw new ForbiddenException("You are not allowed to modify this quote");
        }
    }
}
