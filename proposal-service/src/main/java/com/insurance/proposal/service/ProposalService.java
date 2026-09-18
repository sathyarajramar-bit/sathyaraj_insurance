package com.insurance.proposal.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.QuoteExpiredException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.proposal.client.CustomerClient;
import com.insurance.proposal.client.CustomerGateway;
import com.insurance.proposal.client.QuoteClient;
import com.insurance.proposal.client.QuoteGateway;
import com.insurance.proposal.config.ProposalProperties;
import com.insurance.proposal.dto.DecisionRequest;
import com.insurance.proposal.dto.ProposalResponse;
import com.insurance.proposal.dto.StatusHistoryResponse;
import com.insurance.proposal.dto.UpdateProposalRequest;
import com.insurance.proposal.entity.Nominee;
import com.insurance.proposal.entity.Proposal;
import com.insurance.proposal.entity.ProposalStatus;
import com.insurance.proposal.entity.Proposer;
import com.insurance.proposal.event.ProposalEventPublisher;
import com.insurance.proposal.exception.InvalidProposalException;
import com.insurance.proposal.exception.ProposalNotFoundException;
import com.insurance.proposal.exception.ProposalStateException;
import com.insurance.proposal.mapper.ProposalMapper;
import com.insurance.proposal.repository.ProposalRepository;
import com.insurance.proposal.repository.ProposalSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Proposal lifecycle. Creation reads the quote and the customer profile (outside the transaction),
 * then persists a DRAFT prefilled with what we know. Every transition goes through
 * {@link Proposal#transition} which validates it and records the audit row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final QuoteGateway quoteGateway;
    private final CustomerGateway customerGateway;
    private final ProposalNumberGenerator numberGenerator;
    private final ProposalAccessPolicy accessPolicy;
    private final ProposalMapper mapper;
    private final ProposalProperties properties;
    private final ProposalEventPublisher events;

    public ProposalResponse createFromQuote(String quoteNumber) {
        AuthenticatedUser user = CurrentUser.require();
        proposalRepository.findByQuoteNumber(quoteNumber).ifPresent(existing -> {
            throw new DuplicateResourceException("Quote " + quoteNumber + " already has proposal " + existing.getProposalNumber());
        });
        QuoteClient.QuoteView quote = quoteGateway.getQuote(quoteNumber);
        if (!quote.userId().equals(user.userId()) && !user.hasAnyRole("ADMIN", "AGENT")) {
            throw new ForbiddenException("Quote " + quoteNumber + " does not belong to you");
        }
        if ("EXPIRED".equals(quote.status()) || quote.validUntil().isBefore(Instant.now())) {
            throw new QuoteExpiredException(quoteNumber);
        }
        if (!"ACCEPTED".equals(quote.status())) {
            throw new InvalidProposalException("Quote " + quoteNumber + " must be ACCEPTED before a proposal can be created (status "
                    + quote.status() + ")");
        }
        CustomerClient.CustomerProfile customer = customerGateway.getById(quote.customerId());
        Proposal proposal = Proposal.builder()
                .proposalNumber(numberGenerator.next())
                .userId(quote.userId()).customerId(quote.customerId())
                .quoteNumber(quote.quoteNumber()).quoteId(quote.id())
                .productId(quote.productId()).productCode(quote.productCode()).productName(quote.productName())
                .coverageType(quote.coverageType()).termMonths(quote.termMonths())
                .vehicleId(quote.vehicleId()).registrationNumber(quote.registrationNumber())
                .idv(quote.idv()).premiumAmount(quote.finalPremium())
                .proposer(prefill(customer)).nominee(new Nominee())
                .build();
        proposal.transition(ProposalStatus.DRAFT, actor(user), "Created from quote " + quoteNumber);
        Proposal saved = proposalRepository.save(proposal);   // repository save is itself transactional
        log.info("Proposal {} created from quote {} for customer {}", saved.getProposalNumber(), saved.getQuoteNumber(), saved.getCustomerId());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProposalResponse getByNumber(String proposalNumber) {
        Proposal proposal = find(proposalNumber);
        accessPolicy.assertCanRead(CurrentUser.require(), proposal);
        return mapper.toResponse(proposal);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(String proposalNumber) {
        Proposal proposal = find(proposalNumber);
        accessPolicy.assertCanRead(CurrentUser.require(), proposal);
        return proposal.getHistory().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProposalResponse> search(Long customerId, ProposalStatus status, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        Long userFilter = user.hasAnyRole("ADMIN", "AGENT") ? null : user.userId();
        if (userFilter != null && customerId != null) {
            throw new ForbiddenException("Customers can only list their own proposals");
        }
        return PageResponse.from(proposalRepository.findAll(
                ProposalSpecifications.withFilters(userFilter, customerId, status), pageable), mapper::toResponse);
    }

    @Transactional
    public ProposalResponse update(String proposalNumber, UpdateProposalRequest request) {
        Proposal proposal = find(proposalNumber);
        accessPolicy.assertOwner(CurrentUser.require(), proposal);
        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new ProposalStateException("Proposal " + proposalNumber + " can only be edited while DRAFT (status " + proposal.getStatus() + ")");
        }
        proposal.setProposer(mapper.toEntity(request.proposer()));
        proposal.setNominee(mapper.toEntity(request.nominee()));
        proposal.setDeclarationsAccepted(Boolean.TRUE.equals(request.declarationsAccepted()));
        return mapper.toResponse(proposal);
    }

    /**
     * Idempotent: submitting an already SUBMITTED proposal returns it unchanged (a retried request must
     * not fail or duplicate anything). Any other non-DRAFT status is a state error.
     */
    @Transactional
    public ProposalResponse submit(String proposalNumber) {
        AuthenticatedUser user = CurrentUser.require();
        Proposal proposal = find(proposalNumber);
        accessPolicy.assertOwner(user, proposal);
        if (proposal.getStatus() == ProposalStatus.SUBMITTED) {
            return mapper.toResponse(proposal);
        }
        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new ProposalStateException("Proposal " + proposalNumber + " cannot be submitted from status " + proposal.getStatus());
        }
        validateForSubmission(proposal);
        proposal.transition(ProposalStatus.SUBMITTED, actor(user), null);
        proposal.setSubmittedAt(Instant.now());
        ProposalResponse response = mapper.toResponse(proposal);
        events.proposalSubmitted(response);
        log.info("Proposal {} submitted", proposalNumber);
        return response;
    }

    @Transactional
    public ProposalResponse startReview(String proposalNumber) {
        AuthenticatedUser reviewer = CurrentUser.require();
        Proposal proposal = find(proposalNumber);
        transitionOrFail(proposal, ProposalStatus.UNDER_REVIEW, actor(reviewer), null);
        proposal.setReviewedBy(actor(reviewer));
        return mapper.toResponse(proposal);
    }

    @Transactional
    public ProposalResponse approve(String proposalNumber, DecisionRequest decision) {
        return decide(proposalNumber, ProposalStatus.APPROVED, decision == null ? null : decision.reason());
    }

    @Transactional
    public ProposalResponse reject(String proposalNumber, DecisionRequest decision) {
        if (decision == null || decision.reason() == null || decision.reason().isBlank()) {
            throw new InvalidProposalException("A reason is required to reject a proposal");
        }
        return decide(proposalNumber, ProposalStatus.REJECTED, decision.reason());
    }

    private ProposalResponse decide(String proposalNumber, ProposalStatus target, String reason) {
        AuthenticatedUser reviewer = CurrentUser.require();
        Proposal proposal = find(proposalNumber);
        transitionOrFail(proposal, target, actor(reviewer), reason);
        proposal.setReviewedBy(actor(reviewer));
        proposal.setReviewedAt(Instant.now());
        proposal.setDecisionReason(reason);
        ProposalResponse response = mapper.toResponse(proposal);
        events.proposalDecided(response);
        log.info("Proposal {} {} by {}", proposalNumber, target, actor(reviewer));
        return response;
    }

    private static void transitionOrFail(Proposal proposal, ProposalStatus target, String actor, String reason) {
        if (!proposal.getStatus().canTransitionTo(target)) {
            throw new ProposalStateException("Proposal " + proposal.getProposalNumber() + " cannot move from "
                    + proposal.getStatus() + " to " + target);
        }
        proposal.transition(target, actor, reason);
    }

    /** Business validation at submission: completeness, age, declarations and KYC (configurable). */
    void validateForSubmission(Proposal proposal) {
        if (proposal.getProposer() == null || !proposal.getProposer().isComplete()) {
            throw new InvalidProposalException("Proposer details (name, contact, date of birth, address) are incomplete");
        }
        if (Period.between(proposal.getProposer().getDateOfBirth(), LocalDate.now()).getYears() < properties.getMinimumProposerAge()) {
            throw new InvalidProposalException("Proposer must be at least " + properties.getMinimumProposerAge() + " years old");
        }
        if (proposal.getNominee() == null || !proposal.getNominee().isComplete()) {
            throw new InvalidProposalException("Nominee details are required");
        }
        if (!proposal.isDeclarationsAccepted()) {
            throw new InvalidProposalException("Declarations must be accepted before submission");
        }
        if (properties.isRequireKycVerified()) {
            CustomerClient.CustomerProfile customer = customerGateway.getById(proposal.getCustomerId());
            if (!"VERIFIED".equals(customer.kycStatus())) {
                throw new InvalidProposalException("KYC must be VERIFIED before submitting a proposal (current: " + customer.kycStatus() + ")");
            }
        }
    }

    Proposal find(String proposalNumber) {
        return proposalRepository.findByProposalNumber(proposalNumber).orElseThrow(() -> new ProposalNotFoundException(proposalNumber));
    }

    private static Proposer prefill(CustomerClient.CustomerProfile customer) {
        CustomerClient.Address address = customer.address();
        return new Proposer(customer.firstName(), customer.lastName(), customer.email(), customer.phone(), customer.dateOfBirth(),
                address == null ? null : address.line1(), address == null ? null : address.line2(),
                address == null ? null : address.city(), address == null ? null : address.state(),
                address == null ? null : address.postalCode(), address == null ? null : address.country());
    }

    private static String actor(AuthenticatedUser user) {
        return user.hasRole("SERVICE") ? user.email() : "user:" + user.userId();
    }
}
