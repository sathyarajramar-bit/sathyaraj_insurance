package com.insurance.proposal.repository;

import com.insurance.proposal.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProposalRepository extends JpaRepository<Proposal, Long>, JpaSpecificationExecutor<Proposal> {

    Optional<Proposal> findByProposalNumber(String proposalNumber);

    Optional<Proposal> findByQuoteNumber(String quoteNumber);
}
