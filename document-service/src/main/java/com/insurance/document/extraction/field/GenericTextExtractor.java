package com.insurance.document.extraction.field;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.source.ExtractedText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback for document types without a dedicated extractor: returns the text itself plus whatever structure can be
 * found without knowing the layout ("Label: value" lines, dates, long numbers). Enough to prefill a form by hand
 * and to see what a new dedicated extractor would have to parse.
 */
@Component
public class GenericTextExtractor implements FieldExtractor {

    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z][A-Za-z /().-]{1,40}?)\\s*[:\\-]\\s*(.+)$");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{6,20}\\b");

    @Override
    public DocumentType supports() {
        return DocumentType.OTHER;
    }

    @Override
    public ExtractionResult extract(ExtractedText text) {
        String all = text.joined();
        List<String> lines = TextPatterns.lines(all);
        Map<String, String> keyValues = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher m = KEY_VALUE.matcher(TextPatterns.latinPart(line));
            if (m.find()) {
                keyValues.putIfAbsent(toSnake(m.group(1)), m.group(2).strip());
            }
        }
        List<String> numbers = new ArrayList<>();
        Matcher n = LONG_NUMBER.matcher(all);
        while (n.find()) {
            if (!numbers.contains(n.group())) {
                numbers.add(n.group());
            }
        }
        ExtractionResult r = new ExtractionResult();
        r.put("key_values", keyValues, keyValues.isEmpty() ? 0 : 0.7);
        r.put("dates", TextPatterns.allDates(all), 0.9);
        r.put("numbers", numbers, 0.9);
        r.put("lines", lines, 1.0);
        r.put("raw_text", all, 1.0);
        if (lines.isEmpty()) {
            r.warn("No text could be read from the document");
        }
        r.applyOcrConfidence(text.ocrConfidence());
        return r;
    }

    private static String toSnake(String label) {
        return label.strip().toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
