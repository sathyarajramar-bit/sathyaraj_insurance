package com.insurance.claims.mapper;

import com.insurance.claims.dto.ClaimDocumentResponse;
import com.insurance.claims.dto.ClaimHistoryResponse;
import com.insurance.claims.dto.ClaimResponse;
import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimDocument;
import com.insurance.claims.entity.ClaimStatusHistory;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ClaimMapper {

    ClaimResponse toResponse(Claim claim);

    ClaimDocumentResponse toResponse(ClaimDocument document);

    ClaimHistoryResponse toResponse(ClaimStatusHistory history);
}
