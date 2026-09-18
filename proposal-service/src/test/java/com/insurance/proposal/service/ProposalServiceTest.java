package com.insurance.proposal.service;

import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.QuoteExpiredException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.proposal.client.CustomerClient;
import com.insurance.proposal.client.CustomerGateway;
import com.insurance.proposal.client.QuoteClient;
import com.insurance.proposal.client.QuoteGateway;
import com.insurance.proposal.config.ProposalProperties;
import com.insurance.proposal.dto.DecisionRequest;
import com.insurance.proposal.entity.Nominee;
import com.insurance.proposal.entity.NomineeRelationship;
import com.insurance.proposal.entity.Proposal;
import com.insurance.proposal.entity.ProposalStatus;
import com.insurance.proposal.entity.Proposer;
import com.insurance.proposal.event.ProposalEventPublisher;
import com.insurance.proposal.exception.InvalidProposalException;
import com.insurance.proposal.exception.ProposalStateException;
import com.insurance.proposal.mapper.ProposalMapper;
import com.insurance.proposal.repository.ProposalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    @Mock private ProposalRepository repository;
    @Mock private QuoteGateway quoteGateway;
    @Mock private CustomerGateway customerGateway;
    @Mock private ProposalEventPublisher events;

    private ProposalService service;

    @BeforeEach
    void setUp() {
        service = new ProposalService(repository, quoteGateway, customerGateway, new ProposalNumberGenerator(),
                new ProposalAccessPolicy(), Mappers.getMapper(ProposalMapper.class), new ProposalProperties(), events);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static void actAs(long userId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "u@x.com", List.of(roles)), null, List.of()));
    }

    static QuoteClient.QuoteView quote(String status, Instant validUntil, long userId) {
        return new QuoteClient.QuoteView(1L, "QT-1", status, userId, 7L, 1L, "MOTOR-CAR-COMP", "Car", "COMPREHENSIVE", 12,
                5L, "MH12AB1234", new BigDecimal("600000"), new BigDecimal("21730.88"), validUntil);
    }

    static Proposal draft(long userId) {
        Proposal p = Proposal.builder().proposalNumber("PR-1").userId(userId).customerId(7L).quoteNumber("QT-1")
                .proposer(new Proposer("J", "D", "j@x.com", "9876543210", LocalDate.of(1990, 1, 1), "1 St", null, "Pune", "MH", "411001", "India"))
                .nominee(new Nominee("N", NomineeRelationship.SPOUSE, LocalDate.of(1992, 1, 1)))
                .declarationsAccepted(true).build();
        p.transition(ProposalStatus.DRAFT, "test", null);
        return p;
    }

    @Test
    void expiredQuoteCannotBecomeAProposal() {
        actAs(42L, "CUSTOMER");
        when(repository.findByQuoteNumber("QT-1")).thenReturn(Optional.empty());
        when(quoteGateway.getQuote("QT-1")).thenReturn(quote("ACCEPTED", Instant.now().minusSeconds(1), 42L));

        assertThatThrownBy(() -> service.createFromQuote("QT-1")).isInstanceOf(QuoteExpiredException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void onlyAcceptedQuotesBecomeProposals() {
        actAs(42L, "CUSTOMER");
        when(repository.findByQuoteNumber("QT-1")).thenReturn(Optional.empty());
        when(quoteGateway.getQuote("QT-1")).thenReturn(quote("GENERATED", Instant.now().plusSeconds(600), 42L));

        assertThatThrownBy(() -> service.createFromQuote("QT-1"))
                .isInstanceOf(InvalidProposalException.class).hasMessageContaining("must be ACCEPTED");
    }

    @Test
    void customerCannotUseAnotherCustomersQuote() {
        actAs(99L, "CUSTOMER");
        when(repository.findByQuoteNumber("QT-1")).thenReturn(Optional.empty());
        when(quoteGateway.getQuote("QT-1")).thenReturn(quote("ACCEPTED", Instant.now().plusSeconds(600), 42L));

        assertThatThrownBy(() -> service.createFromQuote("QT-1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submissionRequiresVerifiedKyc() {
        actAs(42L, "CUSTOMER");
        when(repository.findByProposalNumber("PR-1")).thenReturn(Optional.of(draft(42L)));
        when(customerGateway.getById(7L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "j@x.com", "J", "D", null, null, "PENDING", null));

        assertThatThrownBy(() -> service.submit("PR-1"))
                .isInstanceOf(InvalidProposalException.class).hasMessageContaining("KYC must be VERIFIED");
    }

    @Test
    void submissionRequiresNomineeAndDeclarations() {
        actAs(42L, "CUSTOMER");
        Proposal p = draft(42L);
        p.setNominee(new Nominee());
        when(repository.findByProposalNumber("PR-1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.submit("PR-1")).hasMessageContaining("Nominee details are required");
    }

    @Test
    void approveIsOnlyAllowedFromSubmittedOrUnderReview() {
        actAs(2L, "AGENT");
        when(repository.findByProposalNumber("PR-1")).thenReturn(Optional.of(draft(42L)));

        assertThatThrownBy(() -> service.approve("PR-1", null)).isInstanceOf(ProposalStateException.class);
    }

    @Test
    void rejectNeedsAReasonAndRecordsHistory() {
        actAs(2L, "AGENT");
        Proposal p = draft(42L);
        p.transition(ProposalStatus.SUBMITTED, "user:42", null);
        when(repository.findByProposalNumber("PR-1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.reject("PR-1", null)).isInstanceOf(InvalidProposalException.class);
        service.reject("PR-1", new DecisionRequest("Vehicle inspection failed"));

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.getHistory()).hasSize(3);
        assertThat(p.getHistory().get(2).getChangedBy()).isEqualTo("user:2");
        assertThat(p.getHistory().get(2).getReason()).isEqualTo("Vehicle inspection failed");
    }
}
