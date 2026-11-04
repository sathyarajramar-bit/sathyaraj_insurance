package com.insurance.document.extraction;

import com.insurance.document.support.TestJwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End to end over HTTP with a digital PDF (text layer, no OCR) and a text file; OCR has its own test. */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ExtractionControllerIT {

    @Autowired private MockMvc mockMvc;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    static final String OTHER = TestJwt.bearer(99, "o@x.com", "CUSTOMER");
    static final String AGENT = TestJwt.bearer(5, "a@x.com", "AGENT");

    static final List<String> CARD_LINES = List.of(
            "UNITED ARAB EMIRATES - DRIVING LICENSE",
            "License No. 0194428",
            "Name: Light Vehicle ANWAR UL HAQ FAZAL HAQ FAZAL HAQ",
            "Nationality: PAKISTAN",
            "Date of Birth: 01/01/1962",
            "Issue Date: 18/09/1995",
            "Expiry Date: 07/01/2031",
            "Place of Issue: Sharjah",
            "Traffic Code No. 3070265345",
            "Permitted Vehicles: Light Vehicle",
            "SHTR24716");

    /** A real PDF with a text layer, built with PDFBox (standard font: Latin only, which is all the test needs). */
    static byte[] licencePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A6);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                float y = page.getMediaBox().getHeight() - 20;
                for (String line : CARD_LINES) {
                    cs.beginText();
                    cs.newLineAtOffset(12, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsDrivingLicenceFromDigitalPdfWithoutStoring() throws Exception {
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "licence.pdf", "application/pdf", licencePdf()))
                        .param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.messageType").value("S"))
                .andExpect(jsonPath("$.response.license_number").value("0194428"))
                .andExpect(jsonPath("$.response.license_authority_id").value("SHTR24716"))
                .andExpect(jsonPath("$.response.license_issue_date").value("18/09/1995"))
                .andExpect(jsonPath("$.response.license_expire_date").value("07/01/2031"))
                .andExpect(jsonPath("$.response.date_of_birth").value("01/01/1962"))
                .andExpect(jsonPath("$.response.name_english").value("ANWAR UL HAQ FAZAL HAQ FAZAL HAQ"))
                .andExpect(jsonPath("$.response.nationality_english").value("PAKISTAN"))
                .andExpect(jsonPath("$.response.nationality_arabic").value("باكستان"))
                .andExpect(jsonPath("$.response.issue_place_english").value("Sharjah"))
                .andExpect(jsonPath("$.response.issue_place_arabic").value("الشارقة"))
                .andExpect(jsonPath("$.response.traffic_code").value("3070265345"))
                .andExpect(jsonPath("$.response.permitted_vehicle.english[0]").value("Light Vehicle"))
                .andExpect(jsonPath("$.response.permitted_vehicle.arabic[0]").value("مركبة خفيفة"))
                .andExpect(jsonPath("$.response.source").value("TEXT_LAYER"))
                .andExpect(jsonPath("$.response.simulated").value(false))
                .andExpect(jsonPath("$.response.driving_experience_years").isNumber())
                .andExpect(jsonPath("$.response.pages").value(1))
                .andExpect(jsonPath("$.response.field_confidence.license_number").value(0.95))
                .andExpect(jsonPath("$.response.document_id").doesNotExist());
    }

    @Test
    void extractsFromAStoredDocumentWithTheDocumentsOwnAccessRules() throws Exception {
        String created = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "licence.pdf", "application/pdf", licencePdf()))
                        .param("referenceType", "PROPOSAL").param("referenceNumber", "PR-77").param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(post("/api/documents/" + id + "/extract").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("S"))
                .andExpect(jsonPath("$.response.document_id").value(id))
                .andExpect(jsonPath("$.response.document_type").value("DRIVING_LICENSE"))
                .andExpect(jsonPath("$.response.license_number").value("0194428"));

        // an agent may extract too; another customer may not; type can be overridden to the generic extractor
        mockMvc.perform(post("/api/documents/" + id + "/extract").header(HttpHeaders.AUTHORIZATION, AGENT)).andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/" + id + "/extract").header(HttpHeaders.AUTHORIZATION, OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/documents/" + id + "/extract?documentType=OTHER").header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.document_type").value("OTHER"))
                .andExpect(jsonPath("$.response.key_values.nationality").value("PAKISTAN"))
                .andExpect(jsonPath("$.response.numbers[?(@=='3070265345')]").exists());
    }

    @Test
    void textFilesGoStraightToTheExtractor() throws Exception {
        byte[] txt = String.join("\n", CARD_LINES).getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "licence.txt", "text/plain", txt))
                        .param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.license_number").value("0194428"))
                .andExpect(jsonPath("$.response.source").value("TEXT_LAYER"));
    }

    @Test
    void failuresUseTheEnvelope() throws Exception {
        // declared as PDF, but the bytes are not a PDF, image or text
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1, 2, 3, 4, 5, 6, 7, 8}))
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messageType").value("E"))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_CONTENT"))
                .andExpect(jsonPath("$.response").doesNotExist());

        // content type not allowed at all -> the platform's normal 400 validation error
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", licencePdf())))
                .andExpect(status().isUnauthorized());
    }
}
