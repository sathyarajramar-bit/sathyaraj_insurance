package com.insurance.proposal.dto;

import com.insurance.proposal.entity.NomineeRelationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record NomineeDto(
        @NotBlank(message = "Nominee name is required") @Size(max = 150) String name,
        @NotNull(message = "Nominee relationship is required") NomineeRelationship relationship,
        @NotNull(message = "Nominee date of birth is required") @Past LocalDate dateOfBirth) {
}
