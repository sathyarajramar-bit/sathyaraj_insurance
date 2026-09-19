package com.insurance.document.storage;

import com.insurance.document.config.DocumentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Files under {@code document.storage.local-dir/<yyyy>/<MM>/<key>}; keys never contain client input (no path traversal). */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "document.storage", name = "provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFileSystemStorage implements DocumentStorage {

    private final Path baseDir;

    public LocalFileSystemStorage(DocumentProperties properties) {
        this.baseDir = Path.of(properties.getStorage().getLocalDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create document storage directory " + baseDir, e);
        }
        log.info("Local document storage at {}", baseDir);
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public String store(String suggestedKey, byte[] content) {
        Path target = resolve(suggestedKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write document " + suggestedKey, e);
        }
        return suggestedKey;
    }

    @Override
    public InputStream read(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read document " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete document " + storageKey, e);
        }
    }

    private Path resolve(String key) {
        Path path = baseDir.resolve(key).normalize();
        if (!path.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
