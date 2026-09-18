package com.insurance.customer.dto;

import com.insurance.customer.entity.FuelType;
import com.insurance.customer.entity.VehicleType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Bean Validation covers shape; business rules (age, uniqueness) live in VehicleService. */
public record VehicleRequest(
        @NotBlank(message = "Vehicle registration number is required")
        @Pattern(regexp = "^[A-Za-z]{2}[ -]?[0-9]{1,2}[ -]?[A-Za-z]{0,3}[ -]?[0-9]{4}$",
                message = "Registration number must look like MH12AB1234 (spaces or hyphens allowed)")
        String registrationNumber,

        @NotNull(message = "Vehicle type is required") VehicleType vehicleType,
        @NotBlank(message = "Make is required") @Size(max = 50) String make,
        @NotBlank(message = "Model is required") @Size(max = 50) String model,
        @Size(max = 50) String variant,
        @NotNull(message = "Fuel type is required") FuelType fuelType,
        @NotNull(message = "Manufacturing year is required") @Min(value = 1980, message = "Manufacturing year is too old") Integer manufacturingYear,
        @NotNull(message = "Engine capacity is required") @Positive(message = "Engine capacity must be positive") Integer engineCapacityCc,
        @Size(max = 30) String chassisNumber,
        @NotNull(message = "Registration date is required") @PastOrPresent(message = "Registration date cannot be in the future") LocalDate registrationDate,
        @NotNull(message = "Current value is required") @Positive(message = "Current value must be positive")
        @Digits(integer = 10, fraction = 2) BigDecimal currentValue) {
}
