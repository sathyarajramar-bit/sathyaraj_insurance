package com.insurance.quote.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contract with customer-service: who the customer is and which vehicle is being insured. */
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerClient {

    @GetMapping("/by-user/{userId}")
    CustomerProfile getByUserId(@PathVariable("userId") Long userId);

    @GetMapping("/{customerId}")
    CustomerProfile getById(@PathVariable("customerId") Long customerId);

    @GetMapping("/{customerId}/vehicles/{vehicleId}")
    Vehicle getVehicle(@PathVariable("customerId") Long customerId, @PathVariable("vehicleId") Long vehicleId);

    record CustomerProfile(Long id, Long userId, String email, String firstName, String lastName, LocalDate dateOfBirth,
                           String kycStatus) {
    }

    record Vehicle(Long id, Long customerId, String registrationNumber, String vehicleType, String make, String model,
                   String fuelType, Integer manufacturingYear, Integer engineCapacityCc, BigDecimal currentValue) {
    }
}
