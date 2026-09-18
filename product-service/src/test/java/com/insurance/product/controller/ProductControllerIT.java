package com.insurance.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.product.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full slice on H2 + Flyway (including the seeded catalogue) + real security chain + in-memory cache. */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProductControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    static final String PRODUCT_JSON = """
            {"code":"%s","name":"Test Car Cover","productType":"MOTOR","vehicleType":"CAR","coverageType":"COMPREHENSIVE",
             "termMonths":12,"active":%s,"effectiveFrom":"2026-01-01",
             "coverages":[{"code":"OWN_DAMAGE","name":"Own damage","sumInsuredType":"IDV","mandatory":true,"displayOrder":1},
                          {"code":"TP","name":"Third party","sumInsuredType":"FIXED","fixedSumInsured":750000,"mandatory":true,"displayOrder":2}],
             "addOns":[{"code":"ZERO_DEP","name":"Zero depreciation","pricingType":"PERCENT_OF_IDV","rate":0.4}],
             "eligibilityRules":[{"ruleType":"MAX_VEHICLE_AGE_YEARS","ruleValue":"10","message":"Car must be under 10 years"}],
             "pricing":{"baseRatePercentOfIdv":2.5,"minBasePremium":2500,"thirdPartyPremium":3416,"vehicleAgeLoadingPercentPerYear":2,
                        "maxVehicleAgeLoadingPercent":20,"youngDriverAgeLimit":25,"youngDriverLoadingPercent":10,
                        "maxNcbDiscountPercent":50,"electricVehicleDiscountPercent":15,"taxPercent":18}}""";

    static final String SINGLE_COVERAGE_JSON = """
            {"code":"%s","name":"Renamed Cover","productType":"MOTOR","vehicleType":"CAR","coverageType":"COMPREHENSIVE",
             "termMonths":12,"active":true,"effectiveFrom":"2026-01-01",
             "coverages":[{"code":"OWN_DAMAGE","name":"Own damage","sumInsuredType":"IDV","mandatory":true,"displayOrder":1}],
             "addOns":[{"code":"ZERO_DEP","name":"Zero depreciation","pricingType":"PERCENT_OF_IDV","rate":0.5}],
             "pricing":{"baseRatePercentOfIdv":2.5,"minBasePremium":2500,"thirdPartyPremium":3416,"vehicleAgeLoadingPercentPerYear":2,
                        "maxVehicleAgeLoadingPercent":20,"youngDriverAgeLimit":25,"youngDriverLoadingPercent":10,
                        "maxNcbDiscountPercent":50,"electricVehicleDiscountPercent":15,"taxPercent":18}}""";

    static final String RISK_JSON = """
            {"vehicleType":"%s","vehicleAgeYears":%d,"idv":650000,"engineCapacityCc":1498,"fuelType":"PETROL","driverAge":%d}""";

    @Test
    void seededCatalogueIsPublicWithFiltersAndPagination() throws Exception {
        mockMvc.perform(get("/api/products?type=MOTOR&page=0&size=50&sort=code,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code=='MOTOR-BIKE-TP')]").exists())
                .andExpect(jsonPath("$.content[?(@.code=='MOTOR-CAR-COMP')]").exists())
                .andExpect(jsonPath("$.content[0].coverages").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));

        mockMvc.perform(get("/api/products?vehicleType=BIKE&coverageType=THIRD_PARTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("MOTOR-BIKE-TP"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MOTOR-CAR-COMP"))
                .andExpect(jsonPath("$.coverages.length()").value(3))
                .andExpect(jsonPath("$.addOns.length()").value(4))
                .andExpect(jsonPath("$.eligibilityRules.length()").value(4))
                .andExpect(jsonPath("$.pricing").doesNotExist());

        mockMvc.perform(get("/api/products/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found with id 9999"));
    }

    @Test
    void pricingIsHiddenFromCustomersButReadableByServices() throws Exception {
        mockMvc.perform(get("/api/products/1/pricing")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products/1/pricing").header(HttpHeaders.AUTHORIZATION, TestJwt.customer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/products/1/pricing").header(HttpHeaders.AUTHORIZATION, TestJwt.service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("MOTOR-CAR-COMP"))
                .andExpect(jsonPath("$.baseRatePercentOfIdv").value(2.5))
                .andExpect(jsonPath("$.taxPercent").value(18.0));
    }

    @Test
    void eligibilityCheckReturnsAllViolations() throws Exception {
        mockMvc.perform(post("/api/products/1/eligibility-check").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.service()).content(RISK_JSON.formatted("CAR", 3, 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.violations").isEmpty());

        mockMvc.perform(post("/api/products/1/eligibility-check").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.customer()).content(RISK_JSON.formatted("BIKE", 20, 17)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.violations.length()").value(3));

        mockMvc.perform(post("/api/products/1/eligibility-check").contentType(MediaType.APPLICATION_JSON)
                        .content(RISK_JSON.formatted("CAR", 3, 30)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminManagesProductLifecycle_customersCannot() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.customer()).content(PRODUCT_JSON.formatted("TEST-CAR-1", true)))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()).content(PRODUCT_JSON.formatted("TEST-CAR-1", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEST-CAR-1"))
                .andExpect(jsonPath("$.coverages.length()").value(2))
                .andExpect(jsonPath("$.addOns[0].code").value("ZERO_DEP"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // duplicate code -> 409
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()).content(PRODUCT_JSON.formatted("TEST-CAR-1", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        // PUT replaces the aggregate (one coverage removed = orphan deleted)
        String updated = SINGLE_COVERAGE_JSON.formatted("TEST-CAR-1");
        mockMvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Cover"))
                .andExpect(jsonPath("$.coverages.length()").value(1));

        // code is immutable
        mockMvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()).content(PRODUCT_JSON.formatted("OTHER-CODE", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product code cannot be changed (policies reference it)"));

        // DELETE = deactivate; anonymous catalogue no longer lists it, admin can still see it with active=false
        mockMvc.perform(delete("/api/products/" + id).header(HttpHeaders.AUTHORIZATION, TestJwt.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/products?type=MOTOR&vehicleType=CAR&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code=='TEST-CAR-1')]").doesNotExist());
        mockMvc.perform(get("/api/products?active=false").header(HttpHeaders.AUTHORIZATION, TestJwt.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code=='TEST-CAR-1')]").exists());

        mockMvc.perform(patch("/api/products/" + id + "/activate").header(HttpHeaders.AUTHORIZATION, TestJwt.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        String invalid = PRODUCT_JSON.formatted("bad code", true).replace("\"termMonths\":12", "\"termMonths\":0");
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='code')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='termMonths')]").exists());

        String motorWithoutVehicle = PRODUCT_JSON.formatted("TEST-CAR-2", true).replace("\"vehicleType\":\"CAR\",", "");
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()).content(motorWithoutVehicle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Motor products must specify a vehicle type"));
    }
}
