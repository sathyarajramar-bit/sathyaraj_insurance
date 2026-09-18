package com.insurance.customer.mapper;

import com.insurance.customer.dto.AddressDto;
import com.insurance.customer.dto.CustomerResponse;
import com.insurance.customer.dto.UpdateCustomerRequest;
import com.insurance.customer.entity.Address;
import com.insurance.customer.entity.Customer;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);

    AddressDto toDto(Address address);

    Address toEntity(AddressDto dto);

    /** Applies an update in place; audit columns, ids and KYC state are never touched by the customer. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "kycDocumentType", ignore = true)
    @Mapping(target = "kycDocumentNumber", ignore = true)
    @Mapping(target = "kycVerifiedAt", ignore = true)
    void updateFromRequest(UpdateCustomerRequest request, @MappingTarget Customer customer);
}
