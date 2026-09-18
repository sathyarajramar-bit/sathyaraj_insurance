package com.insurance.proposal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code proposal.*}: underwriting rules that differ per market/environment. */
@Getter
@Setter
@ConfigurationProperties(prefix = "proposal")
public class ProposalProperties {

    /** Whether the customer's KYC must be VERIFIED before a proposal can be submitted. */
    private boolean requireKycVerified = true;

    /** Minimum age of the proposer at submission. */
    private int minimumProposerAge = 18;
}
