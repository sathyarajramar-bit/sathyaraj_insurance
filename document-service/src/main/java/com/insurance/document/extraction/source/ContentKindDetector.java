package com.insurance.document.extraction.source;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Sniffs the first bytes. Clients lie about content types (a renamed .exe, a PNG called .jpg), and a "PDF" from a
 * phone scanner app is really an image inside a PDF wrapper, so routing decisions are made on the bytes only.
 */
@Component
public class ContentKindDetector {

    private static final byte[] PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF = "GIF8".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BMP = {'B', 'M'};
    private static final byte[] TIFF_LE = {'I', 'I', 0x2A, 0x00};
    private static final byte[] TIFF_BE = {'M', 'M', 0x00, 0x2A};

    public ContentKind detect(byte[] content, String declaredContentType) {
        if (content == null || content.length < 4) {
            return ContentKind.UNKNOWN;
        }
        if (startsWith(content, PDF)) {
            return ContentKind.PDF;
        }
        if (startsWith(content, PNG) || startsWith(content, JPEG) || startsWith(content, GIF) || startsWith(content, BMP)
                || startsWith(content, TIFF_LE) || startsWith(content, TIFF_BE)) {
            return ContentKind.IMAGE;
        }
        if (declaredContentType != null && declaredContentType.toLowerCase().startsWith("text/") && looksLikeText(content)) {
            return ContentKind.TEXT;
        }
        return ContentKind.UNKNOWN;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** No NUL bytes in the first KB is a good enough "it is text" test for the text/plain upload path. */
    private static boolean looksLikeText(byte[] content) {
        int limit = Math.min(content.length, 1024);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return false;
            }
        }
        return true;
    }
}
