package com.cloudnotes.storage;

import java.io.InputStream;
import java.time.Instant;

public interface FileStorageService {

    void upload(String storageKey, InputStream inputStream, long sizeBytes, String contentType);

    PresignedDownloadUrl createPresignedDownloadUrl(String storageKey, String originalFilename);

    void delete(String storageKey);

    record PresignedDownloadUrl(String url, Instant expiresAt) {}
}
