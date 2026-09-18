package com.insurance.customer.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.customer.dto.VehicleRequest;
import com.insurance.customer.dto.VehicleResponse;
import com.insurance.customer.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Vehicles", description = "Insurable vehicles of a customer")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping("/me/vehicles")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Add a vehicle to my profile")
    public ResponseEntity<VehicleResponse> add(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.addToMine(request));
    }

    @GetMapping("/me/vehicles")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "My vehicles, paginated")
    public PageResponse<VehicleResponse> mine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return vehicleService.listMine(pageable);
    }

    @PutMapping("/me/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update one of my vehicles")
    public VehicleResponse update(@PathVariable Long vehicleId, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.updateMine(vehicleId, request);
    }

    @DeleteMapping("/me/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Remove one of my vehicles")
    public ResponseEntity<Void> delete(@PathVariable Long vehicleId) {
        vehicleService.deleteMine(vehicleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{customerId}/vehicles")
    @Operation(summary = "Vehicles of a customer: owner, ADMIN, AGENT or SERVICE")
    public PageResponse<VehicleResponse> listForCustomer(
            @PathVariable Long customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return vehicleService.listForCustomer(customerId, pageable);
    }

    @GetMapping("/{customerId}/vehicles/{vehicleId}")
    @Operation(summary = "One vehicle of a customer (quote-service reads this)")
    public VehicleResponse getForCustomer(@PathVariable Long customerId, @PathVariable Long vehicleId) {
        return vehicleService.getForCustomer(customerId, vehicleId);
    }
}
