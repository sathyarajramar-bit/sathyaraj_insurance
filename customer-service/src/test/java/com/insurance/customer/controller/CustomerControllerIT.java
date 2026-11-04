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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void vehicleImportIsAllOrNothingAndCustomerOnly() throws Exception {
        createProfile(107);
        String me = TestJwt.bearer(107, "user107@example.com", "CUSTOMER");
        int year = Year.now().getValue() - 3;
        String header = "registrationNumber,vehicleType,make,model,fuelType,manufacturingYear,engineCapacityCc,registrationDate,currentValue\n";
        String good = header
                + "GJ05AB1234,CAR,Honda,City,PETROL,%d,1498,%d-06-01,650000\n".formatted(year, year)
                + "KA01ZZ8888,BIKE,Hero,Splendor,PETROL,%d,97,%d-06-01,55000\n".formatted(year, year);
        String bad = header
                + "GJ05AB1234,CAR,Honda,City,PETROL,%d,1498,%d-06-01,650000\n".formatted(year, year)   // now a duplicate in DB
                + "TN09CD4321,CAR,Honda,City,PETROL,%d,1498,%d-06-01,650000\n".formatted(year - 30, year - 30);

        // template download
        byte[] template = mockMvc.perform(get("/api/customers/me/vehicles/import-template").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vehicle-import-template.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(template).startsWith((byte) 'P', (byte) 'K');   // a zip, i.e. an xlsx

        // dry run saves nothing
        mockMvc.perform(multipart("/api/customers/me/vehicles/import?dryRun=true")
                        .file(csv(good)).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.totalRows").value(2));
        mockMvc.perform(get("/api/customers/me/vehicles").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(jsonPath("$.totalElements").value(0));

        // real import
        mockMvc.perform(multipart("/api/customers/me/vehicles/import").file(csv(good)).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IMPORTED"))
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.vehicles[1].registrationNumber").value("KA01ZZ8888"));
        mockMvc.perform(get("/api/customers/me/vehicles").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(jsonPath("$.totalElements").value(2));

        // one existing + one too old: nothing saved, both problems reported with their row numbers
        mockMvc.perform(multipart("/api/customers/me/vehicles/import").file(csv(bad)).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].message").value("Vehicle GJ05AB1234 is already registered"))
                .andExpect(jsonPath("$.errors[1].row").value(3))
                .andExpect(jsonPath("$.errors[1].message").value("Vehicles older than 20 years are not eligible for insurance"));
        mockMvc.perform(get("/api/customers/me/vehicles").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(jsonPath("$.totalElements").value(2));

        // file problems are the platform's 400
        mockMvc.perform(multipart("/api/customers/me/vehicles/import").file(csv("make,model\nHonda,City\n")).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        // row cap (5 in application-test.yml)
        StringBuilder many = new StringBuilder(header);
        for (int i = 0; i < 6; i++) {
            many.append("DL0%dAA%04d,CAR,Honda,City,PETROL,%d,1498,%d-06-01,650000\n".formatted(i + 1, 1000 + i, year, year));
        }
        mockMvc.perform(multipart("/api/customers/me/vehicles/import").file(csv(many.toString())).header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file has 6 vehicles; at most 5 can be imported at once"));

        // staff cannot use the customer's import
        mockMvc.perform(multipart("/api/customers/me/vehicles/import").file(csv(good))
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(5, "agent@x.com", "AGENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/customers/me/vehicles/import").file(csv(good)))
                .andExpect(status().isUnauthorized());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "vehicles.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingProfileIsNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/me").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(777, "n@x.com", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }
}
