package com.insurance.proposal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Completes a DRAFT: proposer details, nominee and the declarations checkbox. */
public record UpdateProposalRequest(
        @NotNull(message = "Proposer details are required") @Valid ProposerDto proposer,
        @NotNull(message = "Nominee is required") @Valid NomineeDto nominee,
        @NotNull(message = "declarationsAccepted is required") Boolean declarationsAccepted) {
}
