package com.insurance.proposal.exception;

import com.insurance.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 422: the proposal (or its quote) does not satisfy a business rule for the requested action. */
public class InvalidProposalException extends BusinessException {

    public InvalidProposalException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PROPOSAL", message);
    }
}
