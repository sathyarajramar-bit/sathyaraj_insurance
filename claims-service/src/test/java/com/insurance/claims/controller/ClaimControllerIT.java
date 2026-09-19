package com.insurance.claims.controller;

import com.insurance.claims.client.DocumentClient;
import com.insurance.claims.client.PolicyClient;
import com.insurance.claims.support.TestJwt;
import com.insurance.common.notification.NotificationPublisher;
import feign.FeignException;
import feign.Request;
import feign.Response;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ClaimControllerIT {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PolicyClient policyClient;
    @MockitoBean private DocumentClient documentClient;
    @MockitoBean private NotificationPublisher notifications;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "o@x.com", "CUSTOMER");
    static final String HANDLER = TestJwt.bearer(3, "h@x.com", "CLAIMS_HANDLER");
    static final String ADMIN = TestJwt.bearer(1, "a@x.com", "ADMIN");
    static final LocalDate INCIDENT = LocalDate.now().minusDays(3);

    @BeforeEach
    void stubs() {
        when(policyClient.coverageCheck(eq("PL-1"), any())).thenReturn(new PolicyClient.PolicyView("PL-1", "ACTIVE", 42L, 7L, "MOTOR-CAR-COMP",
                "COMPREHENSIVE", "MH12AB1234", new BigDecimal("600000"), LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(10)));
    }

    private String body(String policy, String type, BigDecimal amount, LocalDate incident) {
        return "{\"policyNumber\":\"" + policy + "\",\"claimType\":\"" + type + "\",\"incidentDate\":\"" + incident
                + "\",\"incidentLocation\":\"MG Road, Pune\",\"description\":\"Rear-ended at a traffic signal, bumper and boot damaged\",\"claimedAmount\":" + amount + "}";
    }

    private String register(String bearer, String json) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.claimNumber");
    }

    @Test
    void registrationRules() throws Exception {
        String number = register(JANE, body("PL-1", "ACCIDENT", new BigDecimal("45000"), INCIDENT));
        mockMvc.perform(get("/api/claims/" + number).header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REGISTERED")).andExpect(jsonPath("$.idv").value(600000.0));
        mockMvc.perform(get("/api/claims/" + number).header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());

        // another customer's policy
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, OTHER)
                        .content(body("PL-1", "ACCIDENT", BigDecimal.TEN, INCIDENT))).andExpect(status().isForbidden());
        // claimed more than IDV
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content(body("PL-1", "ACCIDENT", new BigDecimal("700000"), INCIDENT)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("CLAIM_ERROR"));
        // too late
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content(body("PL-1", "ACCIDENT", BigDecimal.TEN, LocalDate.now().minusDays(45))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("within 30 days")));
        // policy-service says: not covered on that date (422 body is surfaced verbatim)
        String err = "{\"timestamp\":\"2026-09-19T00:00:00Z\",\"status\":422,\"error\":\"POLICY_ERROR\",\"message\":\"Policy PL-X does not cover 2026-09-16 (status EXPIRED)\",\"path\":\"/x\"}";
        when(policyClient.coverageCheck(eq("PL-X"), any())).thenThrow(FeignException.errorStatus("coverageCheck", Response.builder().status(422)
                .request(Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, null, null)).body(err, StandardCharsets.UTF_8).build()));
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content(body("PL-X", "ACCIDENT", BigDecimal.TEN, INCIDENT)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("POLICY_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not cover")));
        // validation
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"policyNumber\":\"PL-1\",\"claimType\":\"ACCIDENT\",\"incidentDate\":\"" + LocalDate.now().plusDays(1) + "\",\"incidentLocation\":\"x\",\"description\":\"short\",\"claimedAmount\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field=='incidentDate')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='description')]").exists());
    }

    @Test
    void fullLifecycle_documents_assessment_approval_settlement_closure_reopen() throws Exception {
        String number = register(JANE, body("PL-1", "ACCIDENT", new BigDecimal("45000"), INCIDENT));

        mockMvc.perform(put("/api/claims/" + number + "/settle").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"settlementReference\":\"UTR1\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/claims/" + number + "/settle").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"settlementReference\":\"UTR1\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot move from REGISTERED to SETTLED")));

        mockMvc.perform(put("/api/claims/" + number + "/review").header(HttpHeaders.AUTHORIZATION, HANDLER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW")).andExpect(jsonPath("$.assessor").value("user:3"));
        mockMvc.perform(put("/api/claims/" + number + "/documents-required").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"documentsRequested\":\"FIR copy and repair estimate\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DOCUMENTS_REQUIRED"));

        when(documentClient.get(10L)).thenReturn(new DocumentClient.DocumentView(10L, 42L, "CLAIM", number, "FIR", "fir.pdf", "UPLOADED"));
        when(documentClient.get(11L)).thenReturn(new DocumentClient.DocumentView(11L, 42L, "CLAIM", "CL-OTHER", "FIR", "x.pdf", "UPLOADED"));
        when(documentClient.get(12L)).thenReturn(new DocumentClient.DocumentView(12L, 99L, "CLAIM", number, "REPAIR_ESTIMATE", "y.pdf", "UPLOADED"));
        mockMvc.perform(put("/api/claims/" + number + "/documents").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"documentIds\":[11]}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/claims/" + number + "/documents").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"documentIds\":[12]}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/claims/" + number + "/documents").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"documentIds\":[10]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.documents[0].documentId").value(10)).andExpect(jsonPath("$.documents[0].fileName").value("fir.pdf"));

        mockMvc.perform(put("/api/claims/" + number + "/assessment").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"assessedAmount\":40000,\"notes\":\"Bumper and boot replacement per estimate\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessedAmount").value(40000.0));
        mockMvc.perform(put("/api/claims/" + number + "/approve").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"approvedAmount\":50000}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceed the claimed amount")));
        mockMvc.perform(put("/api/claims/" + number + "/approve").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"approvedAmount\":40000,\"reason\":\"As assessed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(put("/api/claims/" + number + "/settle").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"settlementReference\":\"UTR123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SETTLED")).andExpect(jsonPath("$.settledAmount").value(40000.0));
        mockMvc.perform(put("/api/claims/" + number + "/close").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"reason\":\"Paid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(put("/api/claims/" + number + "/documents").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"documentIds\":[10]}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/claims/" + number + "/reject").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"reason\":\"x\"}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/claims/" + number + "/reopen").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/claims/" + number + "/reopen").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .content("{\"reason\":\"Customer disputes the settled amount\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW")).andExpect(jsonPath("$.reopenedCount").value(1));

        mockMvc.perform(get("/api/claims/" + number + "/history").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[7].reason").value("REOPENED: Customer disputes the settled amount"));
    }

    @Test
    void searchIsScopedByRole() throws Exception {
        register(JANE, body("PL-1", "THEFT", new BigDecimal("500000"), INCIDENT));
        mockMvc.perform(get("/api/claims?status=REGISTERED&type=THEFT").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(42));
        mockMvc.perform(get("/api/claims").header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/claims?customerId=7").header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/claims?customerId=7&policyNumber=PL-1&incidentFrom=" + INCIDENT.minusDays(1)).header(HttpHeaders.AUTHORIZATION, HANDLER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
