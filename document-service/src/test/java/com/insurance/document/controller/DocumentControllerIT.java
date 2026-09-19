package com.insurance.document.controller;

import com.insurance.document.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DocumentControllerIT {

    @Autowired private MockMvc mockMvc;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "o@x.com", "CUSTOMER");
    static final String HANDLER = TestJwt.bearer(3, "h@x.com", "CLAIMS_HANDLER");
    static final String SERVICE = TestJwt.bearer(0, "policy-service@internal", "SERVICE");

    private long upload(String bearer, String fileName, String contentType, byte[] bytes, String reference) throws Exception {
        String body = mockMvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", fileName, contentType, bytes))
                        .param("referenceType", "CLAIM").param("referenceNumber", reference).param("documentType", "CLAIM_PHOTO")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void uploadDownloadListVerifyDelete() throws Exception {
        byte[] photo = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        long id = upload(JANE, "dent photo.jpg", "image/jpeg", photo, "CL-1");

        mockMvc.perform(get("/api/documents/" + id).header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("dent_photo.jpg"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.sizeBytes").value(photo.length))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        mockMvc.perform(get("/api/documents/" + id + "/content").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("dent_photo.jpg")))
                .andExpect(content().bytes(photo));

        mockMvc.perform(get("/api/documents/" + id).header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/documents/" + id).header(HttpHeaders.AUTHORIZATION, SERVICE)).andExpect(status().isOk());
        mockMvc.perform(get("/api/documents?referenceType=CLAIM&referenceNumber=CL-1").header(HttpHeaders.AUTHORIZATION, HANDLER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id==" + id + ")]").exists());
        mockMvc.perform(get("/api/documents?referenceType=CLAIM&referenceNumber=CL-1").header(HttpHeaders.AUTHORIZATION, OTHER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(patch("/api/documents/" + id + "/status").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .content("{\"status\":\"VERIFIED\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/documents/" + id + "/status").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"status\":\"REJECTED\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/documents/" + id + "/status").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, HANDLER)
                        .content("{\"status\":\"VERIFIED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));

        mockMvc.perform(delete("/api/documents/" + id).header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/documents/" + id).header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/documents/" + id + "/content").header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/documents/" + id).header(HttpHeaders.AUTHORIZATION, JANE)).andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    void uploadValidation() throws Exception {
        mockMvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}))
                        .param("referenceType", "CLAIM").param("referenceNumber", "CL-2").param("documentType", "OTHER")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not allowed")));
        mockMvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "big.png", "image/png", new byte[2048]))
                        .param("referenceType", "CLAIM").param("referenceNumber", "CL-2").param("documentType", "CLAIM_PHOTO")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("maximum size")));
        mockMvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                        .param("referenceType", "CLAIM").param("referenceNumber", "CL-2").param("documentType", "CLAIM_PHOTO"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generatedDocumentsAreServiceOnly() throws Exception {
        String schedule = Base64.getEncoder().encodeToString("POLICY SCHEDULE PL-1".getBytes(StandardCharsets.UTF_8));
        String body = "{\"userId\":42,\"customerId\":7,\"referenceType\":\"POLICY\",\"referenceNumber\":\"PL-1\",\"documentType\":\"POLICY_SCHEDULE\","
                + "\"fileName\":\"PL-1-schedule.txt\",\"contentType\":\"text/plain\",\"contentBase64\":\"" + schedule + "\"}";
        mockMvc.perform(post("/api/documents/generated").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE).content(body))
                .andExpect(status().isForbidden());
        String created = mockMvc.perform(post("/api/documents/generated").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, SERVICE).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerUserId").value(42)).andExpect(jsonPath("$.documentType").value("POLICY_SCHEDULE"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();
        String text = mockMvc.perform(get("/api/documents/" + id + "/content").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(text).isEqualTo("POLICY SCHEDULE PL-1");
    }
}
