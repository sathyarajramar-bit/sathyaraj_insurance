package com.insurance.proposal.dto;

import jakarta.validation.constraints.NotBlank;

/** A proposal starts from an ACCEPTED quote; everything else is prefilled from the customer profile. */
public record CreateProposalRequest(@NotBlank(message = "quoteNumber is required") String quoteNumber) {
}
