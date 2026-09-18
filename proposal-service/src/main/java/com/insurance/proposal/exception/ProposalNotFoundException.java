package com.insurance.proposal.exception;

import com.insurance.common.exception.ResourceNotFoundException;

public class ProposalNotFoundException extends ResourceNotFoundException {

    public ProposalNotFoundException(String proposalNumber) {
        super("Proposal " + proposalNumber + " not found");
    }
}
