package com.insurance.document.extraction.source;

import com.insurance.document.config.DocumentProperties;
import com.insurance.document.extraction.ExtractionException;
import com.insurance.document.extraction.ocr.OcrEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage one of the pipeline: bytes in, text out. The only place that knows about file formats.
 *
 * <ul>
 *   <li>text file: used as is</li>
 *   <li>digital PDF: PDFBox text layer (fast, exact, no OCR)</li>
 *   <li>scanned PDF (text layer empty or tiny): each page is rendered at {@code renderDpi} and OCR'd</li>
 *   <li>image: OCR'd directly</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextSourceRouter {

    private final ContentKindDetector detector;
    private final OcrEngine ocrEngine;
    private final DocumentProperties properties;

    public ExtractedText extract(byte[] content, String declaredContentType) {
        ContentKind kind = detector.detect(content, declaredContentType);
        return switch (kind) {
            case TEXT -> new ExtractedText(List.of(new String(content, StandardCharsets.UTF_8)), ExtractedText.Source.TEXT_LAYER, null);
            case PDF -> fromPdf(content);
            case IMAGE -> fromImage(content);
            case UNKNOWN -> throw new ExtractionException("UNSUPPORTED_CONTENT",
                    "Content is not a PDF, image or text file (declared type " + declaredContentType + ")");
        };
    }

    private ExtractedText fromPdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted()) {
                throw new ExtractionException("PDF_ENCRYPTED", "The PDF is password protected");
            }
            List<String> pages = textLayer(pdf);
            int chars = pages.stream().mapToInt(p -> p.strip().length()).sum();
            if (chars >= properties.getExtraction().getMinTextLayerChars()) {
                log.debug("PDF has a text layer ({} chars over {} pages), skipping OCR", chars, pages.size());
                return new ExtractedText(pages, ExtractedText.Source.TEXT_LAYER, null);
            }
            log.debug("PDF text layer too small ({} chars): treating as a scan", chars);
            requireOcr();
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<String> ocrPages = new ArrayList<>();
            double confidenceSum = 0;
            int confidenceCount = 0;
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                BufferedImage page = renderer.renderImageWithDPI(i, properties.getExtraction().getOcr().getRenderDpi(), ImageType.RGB);
                OcrEngine.OcrResult result = ocrEngine.recognize(page);
                ocrPages.add(result.text());
                if (result.confidence() != null) {
                    confidenceSum += result.confidence();
                    confidenceCount++;
                }
            }
            return new ExtractedText(ocrPages, ExtractedText.Source.OCR, confidenceCount == 0 ? null : confidenceSum / confidenceCount);
        } catch (IOException e) {
            throw new ExtractionException("PDF_UNREADABLE", "The PDF could not be parsed: " + e.getMessage(), e);
        }
    }

    private static List<String> textLayer(PDDocument pdf) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        List<String> pages = new ArrayList<>();
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            pages.add(stripper.getText(pdf));
        }
        return pages;
    }

    private ExtractedText fromImage(byte[] content) {
        requireOcr();
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new ExtractionException("IMAGE_UNREADABLE", "The image could not be decoded: " + e.getMessage(), e);
        }
        if (image == null) {
            throw new ExtractionException("IMAGE_UNREADABLE", "The image format is not supported by the JDK image readers");
        }
        OcrEngine.OcrResult result = ocrEngine.recognize(image);
        return new ExtractedText(List.of(result.text()), ExtractedText.Source.OCR, result.confidence());
    }

    private void requireOcr() {
        if (!ocrEngine.isAvailable()) {
            throw new ExtractionException("OCR_UNAVAILABLE", "This document needs OCR but OCR is not available: " + ocrEngine.unavailableReason());
        }
    }
}
