package com.insurance.customer.dto;

import com.insurance.customer.entity.FuelType;
import com.insurance.customer.entity.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record VehicleResponse(Long id, Long customerId, String registrationNumber, VehicleType vehicleType,
                              String make, String model, String variant, FuelType fuelType, Integer manufacturingYear,
                              Integer engineCapacityCc, String chassisNumber, LocalDate registrationDate,
                              BigDecimal currentValue, Instant createdAt, Instant updatedAt) {
}
