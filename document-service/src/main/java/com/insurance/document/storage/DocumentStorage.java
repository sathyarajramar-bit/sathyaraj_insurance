package com.insurance.document.storage;

import java.io.InputStream;

/**
 * Port for the byte store. {@link LocalFileSystemStorage} is the development adapter; an S3 adapter
 * (putObject/getObject/deleteObject with the same key) drops in behind the same interface, selected by
 * {@code document.storage.provider}.
 */
public interface DocumentStorage {

    String provider();

    /** Stores the bytes and returns the opaque key to fetch them again. */
    String store(String suggestedKey, byte[] content);

    InputStream read(String storageKey);

    void delete(String storageKey);
}
