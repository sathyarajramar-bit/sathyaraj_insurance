package com.insurance.customer.mapper;

import com.insurance.customer.dto.VehicleRequest;
import com.insurance.customer.dto.VehicleResponse;
import com.insurance.customer.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(target = "customerId", source = "customer.id")
    VehicleResponse toResponse(Vehicle vehicle);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    Vehicle toEntity(VehicleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    void updateFromRequest(VehicleRequest request, @MappingTarget Vehicle vehicle);
}
