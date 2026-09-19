package com.insurance.policy.controller;

import com.insurance.common.notification.NotificationPublisher;
import com.insurance.policy.client.DocumentClient;
import com.insurance.policy.client.PaymentClient;
import com.insurance.policy.client.ProposalClient;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import com.insurance.policy.repository.PolicyRepository;
import com.insurance.policy.scheduler.PolicyLifecycleScheduler;
import com.insurance.policy.support.TestJwt;
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
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PolicyControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyLifecycleScheduler scheduler;
    @MockitoBean private ProposalClient proposalClient;
    @MockitoBean private PaymentClient paymentClient;
    @MockitoBean private DocumentClient documentClient;
    @MockitoBean private NotificationPublisher notifications;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "o@x.com", "CUSTOMER");
    static final String ADMIN = TestJwt.bearer(1, "a@x.com", "ADMIN");
    static final String SERVICE = TestJwt.bearer(0, "payment-service@internal", "SERVICE");
    static final AtomicInteger SEQ = new AtomicInteger();

    @BeforeEach
    void stubs() {
        when(proposalClient.get(anyString())).thenAnswer(inv -> new ProposalClient.ProposalView(inv.getArgument(0), "APPROVED", 42L, 7L, "QT-1", 1L,
                "MOTOR-CAR-COMP", "Comprehensive Car", "COMPREHENSIVE", 12, 5L, "MH12AB1234", new BigDecimal("600000"), new BigDecimal("21730.88"),
                new ProposalClient.Proposer("Jane", "Doe", "jane@x.com"), new ProposalClient.Nominee("John", "SPOUSE")));
        when(documentClient.storeGenerated(any())).thenReturn(new DocumentClient.DocumentView(77L, "x", "UPLOADED"));
    }

    private String issueBody(String paymentRef, String proposal) {
        return "{\"paymentReference\":\"" + paymentRef + "\",\"purpose\":\"NEW_POLICY\",\"proposalNumber\":\"" + proposal
                + "\",\"userId\":42,\"customerId\":7,\"amount\":21730.88,\"currency\":\"INR\"}";
    }

    private void stubPayment(String ref, String status, String proposal) {
        when(paymentClient.get(ref)).thenReturn(new PaymentClient.PaymentView(ref, status, "NEW_POLICY", proposal, null, 42L, 7L, new BigDecimal("21730.88")));
    }

    private String issue(String pay, String proposal) throws Exception {
        stubPayment(pay, "SUCCESS", proposal);
        String number = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/policies/issue").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, SERVICE).content(issueBody(pay, proposal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.policyNumber");
        // the async schedule link bumps the ; wait for it before the test mutates the row
        await().untilAsserted(() -> assertThat(policyRepository.findByPolicyNumber(number).orElseThrow().getScheduleDocumentId()).isNotNull());
        return number;
    }

    /** The async schedule link bumps the @Version; tests that mutate the row must wait for it first. */
    private String issueAndWait(String pay, String proposal) throws Exception {
        String number = issue(pay, proposal);
        await().untilAsserted(() -> assertThat(policyRepository.findByPolicyNumber(number).orElseThrow().getScheduleDocumentId()).isNotNull());
        return number;
    }

    @Test
    void issueIsIdempotentAndVerifiesPayment() throws Exception {
        String pay = "PAY-" + SEQ.incrementAndGet();
        String proposal = "PR-" + SEQ.get();
        stubPayment(pay, "SUCCESS", proposal);

        String number = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/policies/issue").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, SERVICE).content(issueBody(pay, proposal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.policyNumber").value(org.hamcrest.Matchers.startsWith("PL-MOTOR-")))
                .andExpect(jsonPath("$.holderName").value("Jane Doe"))
                .andExpect(jsonPath("$.startDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.endDate").value(LocalDate.now().plusMonths(12).minusDays(1).toString()))
                .andExpect(jsonPath("$.renewalEligible").value(false))
                .andReturn().getResponse().getContentAsString(), "$.policyNumber");

        mockMvc.perform(post("/api/policies/issue").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, SERVICE).content(issueBody(pay, proposal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.policyNumber").value(number));

        await().untilAsserted(() -> assertThat(policyRepository.findByPolicyNumber(number).orElseThrow().getScheduleDocumentId()).isEqualTo(77L));
        verify(documentClient, times(1)).storeGenerated(any());
        verify(notifications, times(1)).publish(any());

        mockMvc.perform(post("/api/policies/issue").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content(issueBody(pay, proposal)))
                .andExpect(status().isForbidden());
        String failedPay = "PAY-F" + SEQ.incrementAndGet();
        stubPayment(failedPay, "FAILED", "PR-F");
        mockMvc.perform(post("/api/policies/issue").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, SERVICE).content(issueBody(failedPay, "PR-F")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("POLICY_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requires a SUCCESS payment")));

        mockMvc.perform(get("/api/policies/" + number).header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isOk());
        mockMvc.perform(get("/api/policies/" + number).header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/policies?status=ACTIVE").header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/policies?status=ACTIVE&registrationNumber=mh12ab1234").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.policyNumber=='" + number + "')]").exists());
        mockMvc.perform(get("/api/policies/" + number + "/coverage-check?incidentDate=" + LocalDate.now().plusDays(10)).header(HttpHeaders.AUTHORIZATION, SERVICE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/policies/" + number + "/coverage-check?incidentDate=" + LocalDate.now().plusYears(2)).header(HttpHeaders.AUTHORIZATION, SERVICE))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("POLICY_ERROR"));
    }

    @Test
    void cancellationRules() throws Exception {
        String number = issue("PAY-C" + SEQ.incrementAndGet(), "PR-C" + SEQ.get());
        mockMvc.perform(post("/api/policies/" + number + "/cancel").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, OTHER)
                        .content("{\"reason\":\"not mine\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/policies/" + number + "/cancel").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"reason\":\"sold the car\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.renewalEligible").value(false));
        mockMvc.perform(post("/api/policies/" + number + "/cancel").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"reason\":\"again\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void expiryJobRenewalRemindersAndRenewalIssuance() throws Exception {
        String number = issueAndWait("PAY-R" + SEQ.incrementAndGet(), "PR-R" + SEQ.get());
        Policy policy = policyRepository.findByPolicyNumber(number).orElseThrow();

        policy.setEndDate(LocalDate.now().plusDays(15));
        policyRepository.save(policy);
        assertThat(scheduler.sendRenewalReminders()).isEqualTo(1);
        assertThat(scheduler.sendRenewalReminders()).isZero();
        mockMvc.perform(get("/api/policies/" + number).header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(jsonPath("$.renewalEligible").value(true));

        policy = policyRepository.findByPolicyNumber(number).orElseThrow();
        policy.setEndDate(LocalDate.now().minusDays(1));
        policyRepository.save(policy);
        assertThat(scheduler.expirePolicies()).isGreaterThanOrEqualTo(1);
        assertThat(policyRepository.findByPolicyNumber(number).orElseThrow().getStatus()).isEqualTo(PolicyStatus.EXPIRED);
        mockMvc.perform(post("/api/policies/" + number + "/renew").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.renewalEligible").value(true));

        String renewalPay = "PAY-RN" + SEQ.incrementAndGet();
        when(paymentClient.get(renewalPay)).thenReturn(new PaymentClient.PaymentView(renewalPay, "SUCCESS", "RENEWAL", null, number, 42L, 7L, new BigDecimal("21730.88")));
        String body = "{\"paymentReference\":\"" + renewalPay + "\",\"purpose\":\"RENEWAL\",\"policyNumber\":\"" + number + "\",\"userId\":42,\"customerId\":7,\"amount\":21730.88,\"currency\":\"INR\"}";
        String renewed = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/policies/issue").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, SERVICE).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.renewedFromPolicyNumber").value(number))
                .andExpect(jsonPath("$.startDate").value(LocalDate.now().toString()))
                .andReturn().getResponse().getContentAsString(), "$.policyNumber");
        assertThat(policyRepository.findByPolicyNumber(number).orElseThrow().getRenewedByPolicyNumber()).isEqualTo(renewed);
        mockMvc.perform(get("/api/policies/" + number).header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(jsonPath("$.renewalEligible").value(false))
                .andExpect(jsonPath("$.renewalMessage").value(org.hamcrest.Matchers.containsString("already been renewed")));
    }
}
