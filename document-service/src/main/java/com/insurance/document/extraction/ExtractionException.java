package com.insurance.document.extraction;

/**
 * The document could not be turned into text or fields. Rendered by the controller as the standard
 * {@code messageType=E} envelope (HTTP 422), not as a generic error, because callers branch on the envelope.
 */
public class ExtractionException extends RuntimeException {

    private final String code;

    public ExtractionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ExtractionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
