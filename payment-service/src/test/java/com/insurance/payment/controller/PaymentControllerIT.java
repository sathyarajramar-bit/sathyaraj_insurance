package com.insurance.payment.controller;

import com.insurance.common.notification.NotificationPublisher;
import com.insurance.payment.client.PolicyClient;
import com.insurance.payment.client.ProposalClient;
import com.insurance.payment.entity.OutboxStatus;
import com.insurance.payment.repository.OutboxRepository;
import com.insurance.payment.scheduler.OutboxRetryScheduler;
import com.insurance.payment.support.TestJwt;
import feign.Request;
import feign.RetryableException;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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
class PaymentControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private OutboxRetryScheduler retryScheduler;
    @MockitoBean private ProposalClient proposalClient;
    @MockitoBean private PolicyClient policyClient;
    @MockitoBean private NotificationPublisher notifications;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "o@x.com", "CUSTOMER");
    static final String ADMIN = TestJwt.bearer(1, "a@x.com", "ADMIN");

    @BeforeEach
    void stubs() {
        reset(policyClient);
        when(proposalClient.get(anyString())).thenAnswer(inv -> new ProposalClient.ProposalView(inv.getArgument(0), "APPROVED", 42L, 7L,
                "MOTOR-CAR-COMP", new BigDecimal("21730.88")));
        when(policyClient.issue(any())).thenReturn(new PolicyClient.PolicyView("PL-1", "ACTIVE", 42L, 7L, new BigDecimal("21730.88"), false, null));
    }

    private String pay(String bearer, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer)
                        .header("Idempotency-Key", key).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void successfulPaymentIssuesPolicyThroughTheOutbox_andIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"proposalNumber\":\"PR-OK-1\",\"paymentMethod\":\"CARD\",\"instrument\":\"4111********1111\"}";

        String first = mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", key).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(21730.88))
                .andExpect(jsonPath("$.providerTxnId").value(org.hamcrest.Matchers.startsWith("mock_")))
                .andReturn().getResponse().getContentAsString();
        String reference = com.jayway.jsonpath.JsonPath.read(first, "$.paymentReference");

        await().untilAsserted(() -> assertThat(outboxRepository.findByPaymentReferenceAndEventType(reference, "PaymentSuccessful"))
                .get().extracting(e -> e.getStatus()).isEqualTo(OutboxStatus.DELIVERED));
        verify(policyClient, times(1)).issue(any());

        // same key again: same payment, provider and policy-service not called again
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", key).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentReference").value(reference));
        verify(policyClient, times(1)).issue(any());

        // different key, same proposal: refused, the proposal is already paid
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PAYMENT_ERROR"))
                .andExpect(jsonPath("$.message").value("Proposal PR-OK-1 has already been paid"));

        mockMvc.perform(get("/api/payments/" + reference).header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payments?status=SUCCESS").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.paymentReference=='" + reference + "')]").exists());
        verify(notifications, atLeastOnce()).publish(any());
    }

    @Test
    void declinedCardIsRecordedAsFailed_noOutbox() throws Exception {
        String result = pay(JANE, UUID.randomUUID().toString(), "{\"proposalNumber\":\"PR-DECL\",\"paymentMethod\":\"CARD\",\"instrument\":\"4000********0002\"}");
        assertThat(result).contains("\"status\":\"FAILED\"").contains("Card declined by issuer");
        String reference = com.jayway.jsonpath.JsonPath.read(result, "$.paymentReference");
        assertThat(outboxRepository.findByPaymentReferenceAndEventType(reference, "PaymentSuccessful")).isEmpty();

        // the proposal is NOT paid, so a new attempt with a good instrument is allowed
        String retry = pay(JANE, UUID.randomUUID().toString(), "{\"proposalNumber\":\"PR-DECL\",\"paymentMethod\":\"UPI\",\"instrument\":\"jane@upi\"}");
        assertThat(retry).contains("\"status\":\"SUCCESS\"");
    }

    @Test
    void gatewayErrorIsRecordedAsFailedNotAsServerError() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"proposalNumber\":\"PR-GW\",\"paymentMethod\":\"CARD\",\"instrument\":\"4000********0009\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.startsWith("Payment gateway error")));
    }

    @Test
    void outboxRetriesWhenPolicyServiceIsDown() throws Exception {
        doThrow(new RetryableException(-1, "down", Request.HttpMethod.POST, (Long) null,
                Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, null, null))).when(policyClient).issue(any());
        String result = pay(JANE, UUID.randomUUID().toString(), "{\"proposalNumber\":\"PR-RETRY\",\"paymentMethod\":\"CARD\",\"instrument\":\"4111********1111\"}");
        String reference = com.jayway.jsonpath.JsonPath.read(result, "$.paymentReference");

        await().untilAsserted(() -> assertThat(outboxRepository.findByPaymentReferenceAndEventType(reference, "PaymentSuccessful"))
                .get().satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(e.getAttempts()).isEqualTo(1);
                    assertThat(e.getLastError()).contains("down");
                }));

        reset(policyClient);
        when(policyClient.issue(any())).thenReturn(new PolicyClient.PolicyView("PL-2", "ACTIVE", 42L, 7L, BigDecimal.TEN, false, null));
        await().untilAsserted(() -> {
            retryScheduler.redeliverPending();
            assertThat(outboxRepository.findByPaymentReferenceAndEventType(reference, "PaymentSuccessful"))
                    .get().extracting(e -> e.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        });
    }

    @Test
    void rulesAndRoles() throws Exception {
        when(proposalClient.get("PR-SUB")).thenReturn(new ProposalClient.ProposalView("PR-SUB", "SUBMITTED", 42L, 7L, "P", BigDecimal.TEN));
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).content("{\"proposalNumber\":\"PR-SUB\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must be APPROVED")));

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, OTHER)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).content("{\"proposalNumber\":\"PR-OK-2\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"proposalNumber\":\"PR-OK-2\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).content("{\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exactly one")));

        String paid = pay(JANE, UUID.randomUUID().toString(), "{\"proposalNumber\":\"PR-REFUND\",\"paymentMethod\":\"CARD\",\"instrument\":\"4111********1111\"}");
        String reference = com.jayway.jsonpath.JsonPath.read(paid, "$.paymentReference");
        mockMvc.perform(post("/api/payments/" + reference + "/refund").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"reason\":\"free look\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments/" + reference + "/refund").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .content("{\"reason\":\"free look cancellation\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED")).andExpect(jsonPath("$.refundReference").exists());
        mockMvc.perform(post("/api/payments/" + reference + "/refund").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .content("{\"reason\":\"again\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
