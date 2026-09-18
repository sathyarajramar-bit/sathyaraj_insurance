package com.insurance.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.auth.client.CustomerClient;
import com.insurance.auth.client.CustomerResponse;
import com.insurance.auth.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller -> service -> repository on H2 (MySQL mode) with the real Flyway migration and real
 * security filter chain. Only the network call to customer-service is mocked.
 */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private CustomerClient customerClient;

    private static final String REGISTER = """
            {"email":"%s","password":"Passw0rd1","firstName":"Jane","lastName":"Doe","phone":"9876543210"}""";

    @Test
    void fullSessionLifecycle_register_login_me_refresh_logout() throws Exception {
        when(customerClient.create(any())).thenReturn(new CustomerResponse(1L, 1L, "life@example.com"));

        JsonNode registered = json(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.formatted("life@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("life@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn());

        JsonNode login = json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"LIFE@example.com\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registered.get("user").get("id").asLong()))
                .andExpect(header().exists("X-Correlation-Id"));

        String refresh = login.get("refreshToken").asText();
        JsonNode rotated = json(mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn());

        // The old refresh token was rotated away: presenting it again is treated as a compromise.
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotated.get("accessToken").asText())
                        .content("{\"refreshToken\":\"" + rotated.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        when(customerClient.create(any())).thenReturn(new CustomerResponse(2L, 2L, "dup@example.com"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER.formatted("dup@example.com"))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.formatted("dup@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void weakPasswordIsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"weak@example.com\",\"password\":\"short\",\"firstName\":\"J\",\"lastName\":\"D\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void wrongPasswordIsUnauthorizedWithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void customerCannotUseAdminEndpoints_adminCan() throws Exception {
        mockMvc.perform(get("/api/auth/users").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(5, "c@x.com", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // the bootstrapped admin (application-test.yml) is searchable and pageable
        mockMvc.perform(get("/api/auth/users?email=admin@test&page=0&size=5&sort=createdAt,desc")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "a@x.com", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("admin@test.local"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminCanPromoteUserToAgent() throws Exception {
        when(customerClient.create(any())).thenReturn(new CustomerResponse(3L, 3L, "agent@example.com"));
        JsonNode registered = json(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER.formatted("agent@example.com"))).andReturn());
        long id = registered.get("user").get("id").asLong();

        mockMvc.perform(put("/api/auth/users/" + id + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "a@x.com", "ADMIN"))
                        .content("{\"roles\":[\"CUSTOMER\",\"AGENT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
