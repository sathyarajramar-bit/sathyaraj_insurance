package com.insurance.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sent by auth-service (SERVICE role) right after registration. */
public record CreateCustomerRequest(
        @NotNull(message = "userId is required") Long userId,
        @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
        @NotBlank(message = "First name is required") @Size(max = 100) String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 100) String lastName,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10 to 15 digits") String phone) {
}
