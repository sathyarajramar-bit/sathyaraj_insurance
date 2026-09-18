package com.insurance.proposal.exception;

import com.insurance.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 422: the requested transition is not allowed from the current status. */
public class ProposalStateException extends BusinessException {

    public ProposalStateException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PROPOSAL_INVALID_STATE", message);
    }
}
