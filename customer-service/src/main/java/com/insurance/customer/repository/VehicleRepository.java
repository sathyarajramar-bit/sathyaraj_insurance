package com.insurance.customer.repository;

import com.insurance.customer.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Page<Vehicle> findByCustomerId(Long customerId, Pageable pageable);

    Optional<Vehicle> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByRegistrationNumber(String registrationNumber);
}
