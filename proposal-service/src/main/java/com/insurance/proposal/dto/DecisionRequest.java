package com.insurance.proposal.dto;

import jakarta.validation.constraints.Size;

/** Reviewer's note; required for rejections (validated in the service), optional for approvals. */
public record DecisionRequest(@Size(max = 500) String reason) {
}
