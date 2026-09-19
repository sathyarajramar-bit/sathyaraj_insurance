package com.insurance.notification.controller;

import com.insurance.notification.client.CustomerClient;
import com.insurance.notification.entity.DeliveryStatus;
import com.insurance.notification.repository.NotificationRepository;
import com.insurance.notification.scheduler.NotificationRetryScheduler;
import com.insurance.notification.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationRepository repository;
    @Autowired private NotificationRetryScheduler retry;
    @MockitoBean private CustomerClient customerClient;

    static final String SERVICE = TestJwt.bearer(0, "policy-service@internal", "SERVICE");
    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String ADMIN = TestJwt.bearer(1, "a@x.com", "ADMIN");

    static String event(String type, String key, String email, String ref) {
        return "{\"eventType\":\"" + type + "\",\"userId\":42,\"customerId\":7," + (email == null ? "" : "\"recipientEmail\":\"" + email + "\",")
                + "\"referenceType\":\"POLICY\",\"referenceNumber\":\"" + ref + "\",\"params\":{\"policyNumber\":\"" + ref
                + "\",\"product\":\"Car\",\"startDate\":\"2026-09-20\",\"endDate\":\"2027-09-19\",\"premium\":\"21730.88\"},\"idempotencyKey\":\"" + key + "\"}";
    }

    @Test
    void rendersStoresAndDeliversOnBothChannels_idempotently() throws Exception {
        when(customerClient.getById(7L)).thenReturn(new CustomerClient.CustomerContact(7L, 42L, "jane@x.com", "9876543210", "Jane"));

        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, SERVICE)
                        .content(event("POLICY_ISSUED", "POLICY_ISSUED:PL-1", null, "PL-1")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].recipient").value("jane@x.com"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].subject").value("Policy PL-1 issued"))
                .andExpect(jsonPath("$[0].body").value(org.hamcrest.Matchers.containsString("active from 2026-09-20 to 2027-09-19")))
                .andExpect(jsonPath("$[1].channel").value("SMS"))
                .andExpect(jsonPath("$[1].recipient").value("9876543210"));

        // redelivered event: nothing new is created or sent
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, SERVICE)
                        .content(event("POLICY_ISSUED", "POLICY_ISSUED:PL-1", null, "PL-1")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        mockMvc.perform(get("/api/notifications?referenceType=POLICY&referenceNumber=PL-1").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notifications?referenceType=POLICY&referenceNumber=PL-1").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content(event("POLICY_ISSUED", "x", null, "PL-9"))).andExpect(status().isForbidden());
    }

    @Test
    void failedDeliveryIsRetriedByTheJob() throws Exception {
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, SERVICE)
                        .content(event("POLICY_EXPIRED", "POLICY_EXPIRED:PL-2", "bounce@fail.test", "PL-2")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].lastError").value(org.hamcrest.Matchers.containsString("SMTP")))
                .andExpect(jsonPath("$[0].attempts").value(1));

        assertThat(retry.retryFailed()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByReferenceTypeAndReferenceNumber("POLICY", "PL-2", org.springframework.data.domain.Pageable.unpaged()).getContent())
                .allSatisfy(n -> {
                    assertThat(n.getStatus()).isEqualTo(DeliveryStatus.FAILED);
                    assertThat(n.getAttempts()).isEqualTo(2);
                });
        // after max attempts (3) the job leaves it alone
        retry.retryFailed();
        assertThat(retry.retryFailed()).isZero();
    }
}
