package com.insurance.document.extraction.field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable builder the extractors fill: fields in insertion order (so the JSON reads top to bottom like the card),
 * a confidence per field and validation warnings. Keys are snake_case because that is the contract the consumers
 * of the envelope already use ({@code license_number}, {@code name_english}, ...).
 */
public class ExtractionResult {

    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, Double> fieldConfidence = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public ExtractionResult put(String key, Object value, double confidence) {
        fields.put(key, value);
        if (value != null) {
            fieldConfidence.put(key, round(confidence));
        }
        return this;
    }

    /** A field that was looked for but not found: present in the JSON as null, absent from the confidences. */
    public ExtractionResult missing(String key) {
        fields.put(key, null);
        return this;
    }

    public ExtractionResult warn(String message) {
        warnings.add(message);
        return this;
    }

    public Object get(String key) {
        return fields.get(key);
    }

    public String getString(String key) {
        Object v = fields.get(key);
        return v == null ? null : v.toString();
    }

    public boolean has(String key) {
        return fields.get(key) != null;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public Map<String, Double> fieldConfidence() {
        return fieldConfidence;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Scales every field confidence by the OCR confidence: a 0.9 pattern match on a 0.6 scan is a 0.75 field. */
    public void applyOcrConfidence(Double ocrConfidence) {
        if (ocrConfidence == null) {
            return;
        }
        double factor = 0.5 + 0.5 * Math.max(0, Math.min(1, ocrConfidence));
        fieldConfidence.replaceAll((k, v) -> round(v * factor));
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
