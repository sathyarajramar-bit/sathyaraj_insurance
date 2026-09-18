package com.insurance.customer.security;

import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.customer.entity.Customer;
import org.springframework.stereotype.Component;

/**
 * Ownership rule in one place: a customer sees only their own profile/vehicles; ADMIN and AGENT see
 * every customer; SERVICE (other microservices) may read on behalf of the platform.
 */
@Component
public class CustomerAccessPolicy {

    public boolean canAccess(AuthenticatedUser user, Customer customer) {
        return customer.isOwnedBy(user.userId()) || user.hasAnyRole("ADMIN", "AGENT", "SERVICE");
    }

    public void assertCanAccess(AuthenticatedUser user, Customer customer) {
        if (!canAccess(user, customer)) {
            throw new ForbiddenException("You are not allowed to access this customer");
        }
    }
}
