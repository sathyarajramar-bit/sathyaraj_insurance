package com.insurance.claims.exception;

import com.insurance.common.exception.ResourceNotFoundException;

public class ClaimNotFoundException extends ResourceNotFoundException {

    public ClaimNotFoundException(String claimNumber) {
        super("Claim " + claimNumber + " not found");
    }
}
