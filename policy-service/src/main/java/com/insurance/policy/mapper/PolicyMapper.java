package com.insurance.policy.mapper;

import com.insurance.policy.dto.PolicyResponse;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.service.RenewalPolicy;
import org.mapstruct.Builder;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PolicyMapper {

    @Mapping(target = "renewalEligible", expression = "java(renewal.evaluate(policy).eligible())")
    @Mapping(target = "renewalMessage", expression = "java(renewal.evaluate(policy).message())")
    PolicyResponse toResponse(Policy policy, @Context RenewalPolicy renewal);
}
