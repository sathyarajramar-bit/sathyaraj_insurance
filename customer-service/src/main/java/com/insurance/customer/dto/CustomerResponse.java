package com.insurance.customer.dto;

import com.insurance.customer.entity.Gender;
import com.insurance.customer.entity.KycDocumentType;
import com.insurance.customer.entity.KycStatus;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponse(Long id, Long userId, String email, String firstName, String lastName, String phone,
                               LocalDate dateOfBirth, Gender gender, KycStatus kycStatus,
                               KycDocumentType kycDocumentType, String kycDocumentNumber, Instant kycVerifiedAt,
                               AddressDto address, Instant createdAt, Instant updatedAt) {
}
