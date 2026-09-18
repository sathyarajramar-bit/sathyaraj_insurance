package com.insurance.customer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.customer.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Year;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full slice on H2 (MySQL mode) + Flyway + real security chain. Users are identified purely by their JWT. */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CustomerControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String VEHICLE = """
            {"registrationNumber":"%s","vehicleType":"CAR","make":"Honda","model":"City","variant":"VX",
             "fuelType":"PETROL","manufacturingYear":%d,"engineCapacityCc":1498,
             "registrationDate":"%d-06-01","currentValue":650000.00}""";

    private long createProfile(long userId) throws Exception {
        String body = "{\"userId\":%d,\"email\":\"user%d@example.com\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"phone\":\"9876543210\"}"
                .formatted(userId, userId);
        ResultActions result = mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.service()).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kycStatus").value("PENDING"));
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private static String vehicle(String registration) {
        int year = Year.now().getValue() - 3;
        return VEHICLE.formatted(registration, year, year);
    }

    @Test
    void onlyServiceRoleMayCreateProfiles() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(50, "c@x.com", "CUSTOMER"))
                        .content("{\"userId\":50,\"email\":\"c@x.com\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void profileCreationIsIdempotentPerUser() throws Exception {
        long first = createProfile(101);
        String body = "{\"userId\":101,\"email\":\"user101@example.com\",\"firstName\":\"Jane\",\"lastName\":\"Doe\"}";
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.service()).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first));
    }

    @Test
    void customerUpdatesOwnProfileAndSubmitsKyc_agentVerifies() throws Exception {
        long id = createProfile(102);
        String me = TestJwt.bearer(102, "user102@example.com", "CUSTOMER");

        mockMvc.perform(put("/api/customers/me").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, me)
                        .content("""
                                {"firstName":"Janet","lastName":"Doe","phone":"9876543210","dateOfBirth":"1990-05-20","gender":"FEMALE",
                                 "address":{"line1":"12 MG Road","city":"Pune","state":"MH","postalCode":"411001","country":"India"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.address.city").value("Pune"))
                .andExpect(jsonPath("$.email").value("user102@example.com"));

        mockMvc.perform(put("/api/customers/me/kyc").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, me)
                        .content("{\"documentType\":\"DRIVING_LICENCE\",\"documentNumber\":\"MH12 20090012345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.kycDocumentType").value("DRIVING_LICENCE"));

        mockMvc.perform(patch("/api/customers/" + id + "/kyc").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "agent@x.com", "AGENT"))
                        .content("{\"decision\":\"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.kycVerifiedAt").exists());
    }

    @Test
    void customerCannotReadAnotherCustomer_butAdminAndServiceCan() throws Exception {
        long id = createProfile(103);

        mockMvc.perform(get("/api/customers/" + id).header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(999, "o@x.com", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/customers/" + id).header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(103, "u@x.com", "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customers/" + id).header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "a@x.com", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customers/by-user/103").header(HttpHeaders.AUTHORIZATION, TestJwt.service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void adminSearchSupportsFiltersPaginationAndSorting() throws Exception {
        createProfile(104);
        createProfile(105);
        String admin = TestJwt.bearer(1, "a@x.com", "ADMIN");

        mockMvc.perform(get("/api/customers?email=user10&kycStatus=PENDING&page=0&size=1&sort=email,asc")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/customers?name=nobody-matches").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/customers").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(104, "u@x.com", "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void vehicleLifecycleWithBusinessRules() throws Exception {
        long id = createProfile(106);
        String me = TestJwt.bearer(106, "user106@example.com", "CUSTOMER");

        String created = mockMvc.perform(post("/api/customers/me/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, me).content(vehicle("mh 12 ab 1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value("MH12AB1234"))
                .andExpect(jsonPath("$.customerId").value(id))
                .andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(created).get("id").asLong();

        // duplicate registration number, even with different spacing
        mockMvc.perform(post("/api/customers/me/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, me).content(vehicle("MH12AB1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        // bean validation: bad registration format
        mockMvc.perform(post("/api/customers/me/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, me).content(vehicle("12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("registrationNumber"));

        // business rule: too old
        int old = Year.now().getValue() - 25;
        mockMvc.perform(post("/api/customers/me/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, me).content(VEHICLE.formatted("KA01ZZ9999", old, old)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vehicles older than 20 years are not eligible for insurance"));

        mockMvc.perform(get("/api/customers/me/vehicles?page=0&size=10").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // another customer cannot see it; the platform (SERVICE) can
        mockMvc.perform(get("/api/customers/" + id + "/vehicles/" + vehicleId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(999, "o@x.com", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/" + id + "/vehicles/" + vehicleId).header(HttpHeaders.AUTHORIZATION, TestJwt.service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Honda"));

        mockMvc.perform(delete("/api/customers/me/vehicles/" + vehicleId).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/" + id + "/vehicles/" + vehicleId).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missingProfileIsNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/me").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(777, "n@x.com", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }
}
