package com.insurance.document.extraction.source;

/** What the uploaded bytes actually are, decided from magic numbers rather than the file name or declared type. */
public enum ContentKind {
    PDF, IMAGE, TEXT, UNKNOWN
}
