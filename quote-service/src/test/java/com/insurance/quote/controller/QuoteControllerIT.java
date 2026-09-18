package com.insurance.quote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.quote.client.CustomerClient;
import com.insurance.quote.client.ProductClient;
import com.insurance.quote.entity.Quote;
import com.insurance.quote.entity.QuoteStatus;
import com.insurance.quote.repository.QuoteRepository;
import com.insurance.quote.scheduler.QuoteExpiryScheduler;
import com.insurance.quote.support.TestJwt;
import feign.FeignException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full slice on H2 + Flyway + security + in-memory cache + resilience aspects. The two Feign clients are
 * mocked at the interface level, so the gateways (circuit breaker, retry, error translation) run for real.
 */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class QuoteControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private QuoteExpiryScheduler scheduler;
    @MockitoBean private ProductClient productClient;
    @MockitoBean private CustomerClient customerClient;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "other@x.com", "CUSTOMER");
    static final String AGENT = TestJwt.bearer(2, "agent@x.com", "AGENT");
    static final String SERVICE = TestJwt.bearer(0, "proposal-service@internal", "SERVICE");

    @BeforeEach
    void stubRemoteServices() {
        when(customerClient.getByUserId(42L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "jane@x.com", "Jane", "Doe", LocalDate.of(1990, 5, 20), "VERIFIED"));
        when(customerClient.getVehicle(7L, 5L)).thenReturn(new CustomerClient.Vehicle(5L, 7L, "MH12AB1234", "CAR", "Honda", "City", "PETROL", 2023, 1498, new BigDecimal("600000")));
        when(productClient.getProduct(1L)).thenReturn(new ProductClient.ProductDetails(1L, "MOTOR-CAR-COMP", "Comprehensive Car", "MOTOR", "CAR", "COMPREHENSIVE", 12, true,
                List.of(new ProductClient.ProductAddOn(10L, "ZERO_DEPRECIATION", "Zero Depreciation", "PERCENT_OF_IDV", new BigDecimal("0.4"), true),
                        new ProductClient.ProductAddOn(12L, "ROADSIDE_ASSISTANCE", "Roadside Assistance", "FLAT", new BigDecimal("499"), true))));
        when(productClient.getPricing(1L)).thenReturn(new ProductClient.ProductPricing(1L, "MOTOR-CAR-COMP", new BigDecimal("2.5"), new BigDecimal("2500"),
                new BigDecimal("3416"), new BigDecimal("2"), new BigDecimal("20"), 25, new BigDecimal("10"), new BigDecimal("50"), new BigDecimal("15"), new BigDecimal("18")));
        when(productClient.checkEligibility(eq(1L), any())).thenReturn(new ProductClient.EligibilityResult(1L, "MOTOR-CAR-COMP", true, List.of()));
    }

    private String generate(String bearer, String body) throws Exception {
        return mockMvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void generateQuoteWithAddOnsAndBreakdown() throws Exception {
        String body = generate(JANE, "{\"productId\":1,\"vehicleId\":5,\"addOnCodes\":[\"zero_depreciation\",\"ROADSIDE_ASSISTANCE\"],\"ncbPercent\":20}");
        Map<?, ?> quote = objectMapper.readValue(body, Map.class);

        // OD: 600000*2.5% = 15000, 3 yrs (2026-2023) +6% = 15900; driver 36 no loading; base 19316; add-ons 2400+499; NCB 20% of OD = 3180
        assertThat(quote.get("status")).isEqualTo("GENERATED");
        assertThat(quote.get("quoteNumber").toString()).startsWith("QT-");
        assertThat(new BigDecimal(quote.get("ownDamagePremium").toString())).isEqualByComparingTo("15900.00");
        assertThat(new BigDecimal(quote.get("basePremium").toString())).isEqualByComparingTo("19316.00");
        assertThat(new BigDecimal(quote.get("addOnPremium").toString())).isEqualByComparingTo("2899.00");
        assertThat(new BigDecimal(quote.get("discountAmount").toString())).isEqualByComparingTo("3180.00");
        assertThat(new BigDecimal(quote.get("finalPremium").toString())).isEqualByComparingTo("22461.30");
        assertThat(((List<?>) quote.get("addOns"))).hasSize(2);
        assertThat(quote.get("driverAge")).isEqualTo(36);
        assertThat(quote.get("customerId")).isEqualTo(7);
    }

    @Test
    void previewDoesNotPersist() throws Exception {
        long before = quoteRepository.count();
        mockMvc.perform(post("/api/quotes/preview").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"productId\":1,\"vehicleId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.finalPremium").value(22792.88));
        assertThat(quoteRepository.count()).isEqualTo(before);
    }

    @Test
    void ownershipAndRoles() throws Exception {
        String number = objectMapper.readTree(generate(JANE, "{\"productId\":1,\"vehicleId\":5}")).get("quoteNumber").asText();

        mockMvc.perform(get("/api/quotes/" + number).header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isOk());
        mockMvc.perform(get("/api/quotes/" + number).header(HttpHeaders.AUTHORIZATION, OTHER))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/quotes/" + number).header(HttpHeaders.AUTHORIZATION, SERVICE)).andExpect(status().isOk());
        mockMvc.perform(get("/api/quotes/" + number).header(HttpHeaders.AUTHORIZATION, AGENT)).andExpect(status().isOk());
        mockMvc.perform(get("/api/quotes/" + number)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/quotes/QT-NOPE").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/quotes?status=GENERATED&page=0&size=5&sort=createdAt,desc").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(42))
                .andExpect(jsonPath("$.size").value(5));
        mockMvc.perform(get("/api/quotes?customerId=7").header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/quotes?customerId=7").header(HttpHeaders.AUTHORIZATION, AGENT)).andExpect(status().isOk());
        mockMvc.perform(get("/api/quotes").header(HttpHeaders.AUTHORIZATION, OTHER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void acceptCancelAndExpiryTransitions() throws Exception {
        String number = objectMapper.readTree(generate(JANE, "{\"productId\":1,\"vehicleId\":5}")).get("quoteNumber").asText();

        mockMvc.perform(post("/api/quotes/" + number + "/accept").header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/quotes/" + number + "/accept").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED")).andExpect(jsonPath("$.acceptedAt").exists());
        mockMvc.perform(post("/api/quotes/" + number + "/accept").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("QUOTE_INVALID_STATE"));
        mockMvc.perform(post("/api/quotes/" + number + "/cancel").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // an overdue GENERATED quote: the job marks it EXPIRED and accept fails with QUOTE_EXPIRED
        String overdue = objectMapper.readTree(generate(JANE, "{\"productId\":1,\"vehicleId\":5}")).get("quoteNumber").asText();
        Quote quote = quoteRepository.findByQuoteNumber(overdue).orElseThrow();
        quote.setValidUntil(Instant.now().minusSeconds(60));
        quoteRepository.save(quote);
        assertThat(scheduler.expireOverdueQuotes()).isGreaterThanOrEqualTo(1);
        assertThat(quoteRepository.findByQuoteNumber(overdue).orElseThrow().getStatus()).isEqualTo(QuoteStatus.EXPIRED);
        mockMvc.perform(post("/api/quotes/" + overdue + "/accept").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("QUOTE_EXPIRED"));
        mockMvc.perform(get("/api/quotes/" + overdue).header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void businessAndValidationErrors() throws Exception {
        when(productClient.checkEligibility(eq(1L), any())).thenReturn(new ProductClient.EligibilityResult(1L, "MOTOR-CAR-COMP", false, List.of("Car too old", "Driver too young")));
        mockMvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"productId\":1,\"vehicleId\":5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("QUOTE_INELIGIBLE"))
                .andExpect(jsonPath("$.message").value("Not eligible for MOTOR-CAR-COMP: Car too old; Driver too young"));

        mockMvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"vehicleId\":5,\"ncbPercent\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='productId')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='ncbPercent')]").exists());

        when(customerClient.getVehicle(7L, 77L)).thenThrow(FeignException.errorStatus("getVehicle",
                feign.Response.builder().status(404).request(Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, null, null)).build()));
        mockMvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"productId\":1,\"vehicleId\":77}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle 77 does not belong to customer 7"));
    }

    @Test
    void productServiceOutageBecomes503ThroughTheCircuitBreakerFallback() throws Exception {
        when(productClient.getProduct(1L)).thenThrow(new RetryableException(-1, "connection refused", Request.HttpMethod.GET, (Long) null,
                Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, null, null)));

        mockMvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"productId\":1,\"vehicleId\":5}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("product-service is currently unavailable. Please retry shortly."));
    }
}
