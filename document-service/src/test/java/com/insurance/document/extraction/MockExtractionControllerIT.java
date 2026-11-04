package com.insurance.document.extraction;

import com.insurance.document.support.TestJwt;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same HTTP surface as {@link ExtractionControllerIT}, with {@code document.extraction.provider=MOCK}: no PDFBox
 * text layer, no Tesseract, no network. Security and the upload validation are unchanged; only the provider differs.
 */
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "document.extraction.provider=MOCK"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MockExtractionControllerIT {

    @Autowired private MockMvc mockMvc;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "o@x.com", "CUSTOMER");

    private static MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }

    @Test
    void successScenarioReturnsTheSimulatedLicence() throws Exception {
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(pdf("licence.pdf", ExtractionControllerIT.licencePdf()))
                        .param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("S"))
                .andExpect(jsonPath("$.response.license_number").value("0194428"))
                .andExpect(jsonPath("$.response.license_issue_date").value("18/09/2015"))
                .andExpect(jsonPath("$.response.driving_experience_years").isNumber())
                .andExpect(jsonPath("$.response.source").value("MOCK"))
                .andExpect(jsonPath("$.response.simulated").value(true))
                .andExpect(jsonPath("$.response.document_type").value("DRIVING_LICENSE"))
                .andExpect(jsonPath("$.response.warnings[?(@ =~ /Simulated response.*/)]").exists())
                .andExpect(jsonPath("$.response.document_id").doesNotExist());
    }

    @Test
    void missingFieldsAndProcessingFailureScenarios() throws Exception {
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(pdf("licence.pdf", ExtractionControllerIT.licencePdf()))
                        .param("documentType", "DRIVING_LICENSE").param("scenario", "MISSING_FIELDS")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.license_issue_date").value((Object) null))
                .andExpect(jsonPath("$.response.traffic_code").value((Object) null))
                .andExpect(jsonPath("$.response.warnings[?(@ == 'Required field missing: traffic_code')]").exists());

        mockMvc.perform(multipart("/api/documents/extract")
                        .file(pdf("licence.pdf", ExtractionControllerIT.licencePdf()))
                        .param("documentType", "DRIVING_LICENSE").param("scenario", "processing_failure")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messageType").value("E"))
                .andExpect(jsonPath("$.errorCode").value("OCR_FAILED"))
                .andExpect(jsonPath("$.response").doesNotExist());
    }

    @Test
    void uploadValidationStillApplies() throws Exception {
        // empty file
        mockMvc.perform(multipart("/api/documents/extract").file(pdf("empty.pdf", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("File is empty"));

        // a PNG declared as image/png is allowed by the upload rules but the mock only simulates PDFs
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "card.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}))
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The mock extraction provider accepts PDF files only"));

        // over document.max-size-bytes (2 MB in the test profile)
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(big, (byte) 'x');
        System.arraycopy("%PDF-1.4".getBytes(), 0, big, 0, 8);
        mockMvc.perform(multipart("/api/documents/extract").file(pdf("big.pdf", big))
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("File exceeds the maximum size")));

        // unknown scenario
        mockMvc.perform(multipart("/api/documents/extract").file(pdf("licence.pdf", ExtractionControllerIT.licencePdf()))
                        .param("scenario", "EXPLODE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unknown scenario")));

        // no token
        mockMvc.perform(multipart("/api/documents/extract").file(pdf("licence.pdf", ExtractionControllerIT.licencePdf())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storedDocumentsKeepTheirAccessRules() throws Exception {
        String created = mockMvc.perform(multipart("/api/documents")
                        .file(pdf("licence.pdf", ExtractionControllerIT.licencePdf()))
                        .param("referenceType", "PROPOSAL").param("referenceNumber", "PR-78").param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(post("/api/documents/" + id + "/extract").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.document_id").value(id))
                .andExpect(jsonPath("$.response.simulated").value(true))
                .andExpect(jsonPath("$.response.license_number").value("0194428"));
        mockMvc.perform(post("/api/documents/" + id + "/extract").header(HttpHeaders.AUTHORIZATION, OTHER))
                .andExpect(status().isForbidden());
    }
}
