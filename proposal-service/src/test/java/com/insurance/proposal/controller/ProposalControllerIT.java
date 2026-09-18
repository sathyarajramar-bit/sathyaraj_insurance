package com.insurance.proposal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.proposal.client.CustomerClient;
import com.insurance.proposal.client.QuoteClient;
import com.insurance.proposal.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full slice on H2 + Flyway + security + resilience aspects; quote-service and customer-service are mocked at the Feign interface. */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProposalControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private QuoteClient quoteClient;
    @MockitoBean private CustomerClient customerClient;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "other@x.com", "CUSTOMER");
    static final String AGENT = TestJwt.bearer(2, "agent@x.com", "AGENT");
    static final String SERVICE = TestJwt.bearer(0, "payment-service@internal", "SERVICE");
    static final AtomicInteger QUOTE_SEQ = new AtomicInteger();

    static final String COMPLETE = """
            {"proposer":{"firstName":"Jane","lastName":"Doe","email":"jane@x.com","phone":"9876543210","dateOfBirth":"1990-05-20",
                         "addressLine1":"12 MG Road","city":"Pune","state":"MH","postalCode":"411001","country":"India"},
             "nominee":{"name":"John Doe","relationship":"SPOUSE","dateOfBirth":"1988-01-15"},
             "declarationsAccepted":true}""";

    @BeforeEach
    void stubs() {
        when(quoteClient.getQuote(anyString())).thenAnswer(inv -> new QuoteClient.QuoteView(1L, inv.getArgument(0), "ACCEPTED", 42L, 7L, 1L,
                "MOTOR-CAR-COMP", "Comprehensive Car", "COMPREHENSIVE", 12, 5L, "MH12AB1234", new BigDecimal("600000"),
                new BigDecimal("21730.88"), Instant.now().plusSeconds(3600)));
        when(customerClient.getById(7L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "jane@x.com", "Jane", "Doe", "9876543210",
                LocalDate.of(1990, 5, 20), "VERIFIED", new CustomerClient.Address("12 MG Road", null, "Pune", "MH", "411001", "India")));
    }

    private String newQuoteNumber() {
        return "QT-TEST-" + QUOTE_SEQ.incrementAndGet();
    }

    private String create(String bearer, String quoteNumber) throws Exception {
        String body = mockMvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer)
                        .content("{\"quoteNumber\":\"" + quoteNumber + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("proposalNumber").asText();
    }

    @Test
    void createPrefillsFromQuoteAndCustomer_onePerQuote() throws Exception {
        String quote = newQuoteNumber();
        mockMvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"quoteNumber\":\"" + quote + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.proposalNumber").value(org.hamcrest.Matchers.startsWith("PR-")))
                .andExpect(jsonPath("$.premiumAmount").value(21730.88))
                .andExpect(jsonPath("$.proposer.firstName").value("Jane"))
                .andExpect(jsonPath("$.proposer.city").value("Pune"))
                .andExpect(jsonPath("$.nominee.name").doesNotExist());

        mockMvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"quoteNumber\":\"" + quote + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void quoteMustBeAcceptedAndNotExpiredAndOwned() throws Exception {
        when(quoteClient.getQuote("QT-GEN")).thenReturn(new QuoteClient.QuoteView(2L, "QT-GEN", "GENERATED", 42L, 7L, 1L, "P", "P", "C", 12, 5L, "X", BigDecimal.ONE, BigDecimal.ONE, Instant.now().plusSeconds(60)));
        mockMvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content("{\"quoteNumber\":\"QT-GEN\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("INVALID_PROPOSAL"));

        when(quoteClient.getQuote("QT-OLD")).thenReturn(new QuoteClient.QuoteView(3L, "QT-OLD", "ACCEPTED", 42L, 7L, 1L, "P", "P", "C", 12, 5L, "X", BigDecimal.ONE, BigDecimal.ONE, Instant.now().minusSeconds(60)));
        mockMvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content("{\"quoteNumber\":\"QT-OLD\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("QUOTE_EXPIRED"));

        mockMvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, OTHER).content("{\"quoteNumber\":\"" + newQuoteNumber() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullLifecycle_update_submitIdempotent_review_approve_history() throws Exception {
        String number = create(JANE, newQuoteNumber());

        // incomplete submission is rejected with the first missing item
        mockMvc.perform(post("/api/proposals/" + number + "/submit").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_PROPOSAL"))
                .andExpect(jsonPath("$.message").value("Nominee details are required"));

        mockMvc.perform(put("/api/proposals/" + number).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, OTHER).content(COMPLETE))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/proposals/" + number).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content(COMPLETE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nominee.relationship").value("SPOUSE"))
                .andExpect(jsonPath("$.declarationsAccepted").value(true));

        mockMvc.perform(post("/api/proposals/" + number + "/submit").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.submittedAt").exists());
        // idempotent re-submit
        mockMvc.perform(post("/api/proposals/" + number + "/submit").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        // no edits after submission
        mockMvc.perform(put("/api/proposals/" + number).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content(COMPLETE))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("PROPOSAL_INVALID_STATE"));

        // customers cannot review; agents can
        mockMvc.perform(post("/api/proposals/" + number + "/review").header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/proposals/" + number + "/review").header(HttpHeaders.AUTHORIZATION, AGENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW")).andExpect(jsonPath("$.reviewedBy").value("user:2"));
        mockMvc.perform(post("/api/proposals/" + number + "/approve").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, AGENT)
                        .content("{\"reason\":\"All documents in order\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.reviewedAt").exists());
        mockMvc.perform(post("/api/proposals/" + number + "/reject").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, AGENT)
                        .content("{\"reason\":\"too late\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("PROPOSAL_INVALID_STATE"));

        mockMvc.perform(get("/api/proposals/" + number + "/history").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].toStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[2].toStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$[3].toStatus").value("APPROVED"))
                .andExpect(jsonPath("$[3].reason").value("All documents in order"));

        // payment-service (SERVICE) can read the approved proposal
        mockMvc.perform(get("/api/proposals/" + number).header(HttpHeaders.AUTHORIZATION, SERVICE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.premiumAmount").value(21730.88));
        mockMvc.perform(get("/api/proposals/" + number).header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
    }

    @Test
    void rejectionRequiresReasonAndKycMustBeVerified() throws Exception {
        String number = create(JANE, newQuoteNumber());
        mockMvc.perform(put("/api/proposals/" + number).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content(COMPLETE))
                .andExpect(status().isOk());

        when(customerClient.getById(7L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "jane@x.com", "Jane", "Doe", "9876543210",
                LocalDate.of(1990, 5, 20), "PENDING", null));
        mockMvc.perform(post("/api/proposals/" + number + "/submit").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("KYC must be VERIFIED before submitting a proposal (current: PENDING)"));

        when(customerClient.getById(7L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "jane@x.com", "Jane", "Doe", "9876543210",
                LocalDate.of(1990, 5, 20), "VERIFIED", null));
        mockMvc.perform(post("/api/proposals/" + number + "/submit").header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isOk());

        mockMvc.perform(post("/api/proposals/" + number + "/reject").header(HttpHeaders.AUTHORIZATION, AGENT))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("A reason is required to reject a proposal"));
        mockMvc.perform(post("/api/proposals/" + number + "/reject").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, AGENT)
                        .content("{\"reason\":\"Vehicle inspection failed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.decisionReason").value("Vehicle inspection failed"));
    }

    @Test
    void listingIsScopedByRole() throws Exception {
        create(JANE, newQuoteNumber());
        mockMvc.perform(get("/api/proposals?status=DRAFT&size=50").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(42));
        mockMvc.perform(get("/api/proposals").header(HttpHeaders.AUTHORIZATION, OTHER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/proposals?customerId=7").header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/proposals?customerId=7&status=DRAFT").header(HttpHeaders.AUTHORIZATION, AGENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
