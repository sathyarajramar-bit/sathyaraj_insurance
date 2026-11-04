package com.insurance.document.extraction.source;

import java.util.List;

/**
 * Output of the first pipeline stage: plain text per page, plus how it was obtained. From here on the field
 * extractors do not care whether the bytes were a PDF, a PNG or a text file.
 *
 * @param pages         text of each page, in order (an image is a single page)
 * @param source        TEXT_LAYER (digital PDF / text file) or OCR (scan, photo)
 * @param ocrConfidence mean word confidence 0..1 when OCR ran and confidences were requested, otherwise null
 */
public record ExtractedText(List<String> pages, Source source, Double ocrConfidence) {

    public enum Source { TEXT_LAYER, OCR }

    public String joined() {
        return String.join("\n", pages);
    }

    public boolean isBlank() {
        return pages.stream().allMatch(p -> p == null || p.isBlank());
    }
}
