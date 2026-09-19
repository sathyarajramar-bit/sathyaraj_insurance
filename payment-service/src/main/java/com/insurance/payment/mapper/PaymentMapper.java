package com.insurance.payment.mapper;

import com.insurance.payment.dto.PaymentResponse;
import com.insurance.payment.entity.Payment;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);
}
