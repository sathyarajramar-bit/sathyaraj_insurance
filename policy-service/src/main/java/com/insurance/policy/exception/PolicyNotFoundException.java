package com.insurance.policy.exception;

import com.insurance.common.exception.ResourceNotFoundException;

public class PolicyNotFoundException extends ResourceNotFoundException {

    public PolicyNotFoundException(String policyNumber) {
        super("Policy " + policyNumber + " not found");
    }
}
