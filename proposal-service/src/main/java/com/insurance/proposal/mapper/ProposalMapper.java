package com.insurance.proposal.mapper;

import com.insurance.proposal.dto.NomineeDto;
import com.insurance.proposal.dto.ProposalResponse;
import com.insurance.proposal.dto.ProposerDto;
import com.insurance.proposal.dto.StatusHistoryResponse;
import com.insurance.proposal.entity.Nominee;
import com.insurance.proposal.entity.Proposal;
import com.insurance.proposal.entity.ProposalStatusHistory;
import com.insurance.proposal.entity.Proposer;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ProposalMapper {

    ProposalResponse toResponse(Proposal proposal);

    ProposerDto toDto(Proposer proposer);

    Proposer toEntity(ProposerDto dto);

    NomineeDto toDto(Nominee nominee);

    Nominee toEntity(NomineeDto dto);

    StatusHistoryResponse toResponse(ProposalStatusHistory history);
}
