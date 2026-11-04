package com.insurance.document.extraction;

import com.insurance.document.extraction.ocr.OcrEngine;
import com.insurance.document.support.TestJwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Tesseract stack (native library + language models) on a synthetic card rendered with Java2D:
 * once as a PNG photo, once as a scanned PDF (image inside a PDF, no text layer). Skipped when the tessdata folder
 * is not present, so {@code ./mvnw test} stays green on machines without OCR.
 */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OcrExtractionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OcrEngine ocrEngine;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");

    static final List<String> CARD_LINES = List.of(
            "DRIVING LICENSE",
            "License No. 0194428",
            "Name: ANWAR UL HAQ FAZAL HAQ",
            "Nationality: PAKISTAN",
            "Date of Birth: 01/01/1962",
            "Issue Date: 18/09/1995",
            "Expiry Date: 07/01/2031",
            "Place of Issue: Sharjah",
            "Traffic Code No. 3070265345");

    @BeforeEach
    void requireOcr() {
        assumeTrue(ocrEngine.isAvailable(), "OCR not available: " + ocrEngine.unavailableReason());
    }

    /** Big, clean, black-on-white sans-serif text: what a good scan at 300 dpi looks like to Tesseract. */
    static BufferedImage renderCard() {
        BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 44));
        int y = 100;
        for (String line : CARD_LINES) {
            g.drawString(line, 60, y);
            y += 85;
        }
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** A PDF that only contains the picture: exactly what a phone scanner app produces. */
    static byte[] scannedPdf(BufferedImage img) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = page.getMediaBox().getWidth();
                cs.drawImage(image, 0, page.getMediaBox().getHeight() - w * img.getHeight() / img.getWidth(), w, w * img.getHeight() / img.getWidth());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void ocrReadsALicencePhoto() throws Exception {
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "licence.png", "image/png", png(renderCard())))
                        .param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("S"))
                .andExpect(jsonPath("$.response.source").value("OCR"))
                .andExpect(jsonPath("$.response.ocr_confidence").isNumber())
                .andExpect(jsonPath("$.response.license_number").value("0194428"))
                .andExpect(jsonPath("$.response.license_expire_date").value("07/01/2031"))
                .andExpect(jsonPath("$.response.date_of_birth").value("01/01/1962"))
                .andExpect(jsonPath("$.response.traffic_code").value("3070265345"))
                .andExpect(jsonPath("$.response.nationality_english").value("PAKISTAN"))
                .andExpect(jsonPath("$.response.issue_place_english").value("Sharjah"));
    }

    @Test
    void ocrReadsAScannedPdf() throws Exception {
        mockMvc.perform(multipart("/api/documents/extract")
                        .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", scannedPdf(renderCard())))
                        .param("documentType", "DRIVING_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, JANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("S"))
                .andExpect(jsonPath("$.response.source").value("OCR"))
                .andExpect(jsonPath("$.response.pages").value(1))
                .andExpect(jsonPath("$.response.license_number").value("0194428"))
                .andExpect(jsonPath("$.response.license_expire_date").value("07/01/2031"));
    }
}
