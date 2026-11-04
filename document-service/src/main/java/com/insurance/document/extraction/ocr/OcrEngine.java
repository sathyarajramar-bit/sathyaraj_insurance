package com.insurance.document.extraction.ocr;

import java.awt.image.BufferedImage;

/**
 * Port for optical character recognition. {@link TesseractOcrEngine} is the self-hosted adapter; a cloud adapter
 * (Azure Document Intelligence, AWS Textract, a vision LLM) implements the same methods and is selected by
 * configuration, so the rest of the pipeline never changes.
 */
public interface OcrEngine {

    /** False when the engine cannot run here (no language data, native library missing, switched off). */
    boolean isAvailable();

    /** Human-readable reason for {@link #isAvailable()} being false, surfaced in the messageType=E envelope. */
    String unavailableReason();

    OcrResult recognize(BufferedImage image);

    /**
     * @param text       recognised text with line breaks preserved
     * @param confidence mean word confidence 0..1, or null when the engine did not compute one
     */
    record OcrResult(String text, Double confidence) {
    }
}
