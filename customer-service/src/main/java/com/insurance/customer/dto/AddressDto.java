package com.insurance.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @NotBlank(message = "Address line 1 is required") @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        @NotBlank(message = "City is required") @Size(max = 100) String city,
        @NotBlank(message = "State is required") @Size(max = 100) String state,
        @NotBlank(message = "Postal code is required")
        @Pattern(regexp = "^[A-Za-z0-9 -]{3,10}$", message = "Postal code must be 3 to 10 letters or digits") String postalCode,
        @NotBlank(message = "Country is required") @Size(max = 100) String country) {
}
