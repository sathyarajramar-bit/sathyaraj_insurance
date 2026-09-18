package com.insurance.customer.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.security.CurrentUser;
import com.insurance.customer.config.CustomerProperties;
import com.insurance.customer.dto.VehicleRequest;
import com.insurance.customer.dto.VehicleResponse;
import com.insurance.customer.entity.Customer;
import com.insurance.customer.entity.Vehicle;
import com.insurance.customer.mapper.VehicleMapper;
import com.insurance.customer.repository.VehicleRepository;
import com.insurance.customer.security.CustomerAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Vehicle rules that Bean Validation cannot express (they need config or the database):
 * registration numbers are unique platform-wide and normalised to upper case, the vehicle must not
 * be older than {@code customer.vehicle.max-age-years}, and it cannot be registered before it was built.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CustomerService customerService;
    private final VehicleMapper mapper;
    private final CustomerAccessPolicy accessPolicy;
    private final CustomerProperties properties;

    @Transactional
    public VehicleResponse addToMine(VehicleRequest request) {
        Customer owner = customerService.findMine();
        String registration = normalise(request.registrationNumber());
        validateBusinessRules(request);
        if (vehicleRepository.existsByRegistrationNumber(registration)) {
            throw new DuplicateResourceException("Vehicle " + registration + " is already registered");
        }
        Vehicle vehicle = mapper.toEntity(request);
        vehicle.setRegistrationNumber(registration);
        vehicle.setCustomer(owner);
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle {} ({}) added for customer {}", saved.getId(), registration, owner.getId());
        return mapper.toResponse(saved);
    }

    @Transactional
    public VehicleResponse updateMine(Long vehicleId, VehicleRequest request) {
        Customer owner = customerService.findMine();
        Vehicle vehicle = vehicleRepository.findByIdAndCustomerId(vehicleId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        String registration = normalise(request.registrationNumber());
        validateBusinessRules(request);
        if (!registration.equals(vehicle.getRegistrationNumber()) && vehicleRepository.existsByRegistrationNumber(registration)) {
            throw new DuplicateResourceException("Vehicle " + registration + " is already registered");
        }
        mapper.updateFromRequest(request, vehicle);
        vehicle.setRegistrationNumber(registration);
        return mapper.toResponse(vehicle);
    }

    @Transactional
    public void deleteMine(Long vehicleId) {
        Customer owner = customerService.findMine();
        Vehicle vehicle = vehicleRepository.findByIdAndCustomerId(vehicleId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        vehicleRepository.delete(vehicle);
        log.info("Vehicle {} deleted by customer {}", vehicleId, owner.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> listMine(Pageable pageable) {
        return PageResponse.from(vehicleRepository.findByCustomerId(customerService.findMine().getId(), pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> listForCustomer(Long customerId, Pageable pageable) {
        Customer customer = customerService.find(customerId);
        accessPolicy.assertCanAccess(CurrentUser.require(), customer);
        return PageResponse.from(vehicleRepository.findByCustomerId(customerId, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getForCustomer(Long customerId, Long vehicleId) {
        Customer customer = customerService.find(customerId);
        accessPolicy.assertCanAccess(CurrentUser.require(), customer);
        return vehicleRepository.findByIdAndCustomerId(vehicleId, customerId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
    }

    void validateBusinessRules(VehicleRequest request) {
        int currentYear = Year.now().getValue();
        if (request.manufacturingYear() > currentYear) {
            throw new ValidationException("Manufacturing year cannot be in the future");
        }
        int maxAge = properties.getVehicle().getMaxAgeYears();
        if (currentYear - request.manufacturingYear() > maxAge) {
            throw new ValidationException("Vehicles older than " + maxAge + " years are not eligible for insurance");
        }
        if (request.registrationDate().getYear() < request.manufacturingYear()) {
            throw new ValidationException("Registration date cannot be before the manufacturing year");
        }
    }

    static String normalise(String registrationNumber) {
        return registrationNumber.replaceAll("[\\s-]", "").toUpperCase();
    }
}
