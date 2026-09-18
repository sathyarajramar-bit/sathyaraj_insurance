package com.insurance.proposal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProposerDto(
        @NotBlank(message = "First name is required") @Size(max = 100) String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 100) String lastName,
        @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
        @NotBlank(message = "Phone is required") @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone must be 10 to 15 digits") String phone,
        @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        @NotBlank(message = "Address line 1 is required") @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank(message = "City is required") @Size(max = 100) String city,
        @NotBlank(message = "State is required") @Size(max = 100) String state,
        @NotBlank(message = "Postal code is required") @Size(max = 20) String postalCode,
        @NotBlank(message = "Country is required") @Size(max = 100) String country) {
}
