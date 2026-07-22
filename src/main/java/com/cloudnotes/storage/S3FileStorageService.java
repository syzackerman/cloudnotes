package com.cloudnotes.storage;

import com.cloudnotes.config.StorageProperties;
import com.cloudnotes.exception.StorageException;
import java.io.InputStream;
import java.time.Instant;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    public S3FileStorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
    }

    @Override
    public void upload(String storageKey, InputStream inputStream, long sizeBytes, String contentType) {
        requireBucket();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(storageKey)
                    .contentLength(sizeBytes)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, sizeBytes));
        } catch (RuntimeException ex) {
            throw new StorageException("S3 upload failed", ex);
        }
    }

    @Override
    public PresignedDownloadUrl createPresignedDownloadUrl(String storageKey, String originalFilename) {
        requireBucket();
        Instant expiresAt = Instant.now().plus(storageProperties.downloadUrlExpiration());
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(storageKey)
                    .responseContentDisposition(
                            "attachment; filename=\"" + sanitizeHeaderFilename(originalFilename) + "\"")
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(storageProperties.downloadUrlExpiration())
                    .getObjectRequest(getObjectRequest)
                    .build();
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return new PresignedDownloadUrl(presignedRequest.url().toString(), expiresAt);
        } catch (RuntimeException ex) {
            throw new StorageException("S3 presigning failed", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        requireBucket();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(storageKey)
                    .build());
        } catch (NoSuchKeyException ex) {
            // Treat already-missing objects as deleted so metadata cleanup can proceed.
        } catch (RuntimeException ex) {
            throw new StorageException("S3 delete failed", ex);
        }
    }

    private void requireBucket() {
        if (storageProperties.bucket() == null || storageProperties.bucket().isBlank()) {
            throw new StorageException("S3 bucket is not configured");
        }
    }

    private String sanitizeHeaderFilename(String filename) {
        return filename == null ? "download" : filename.replace("\\", "_").replace("\"", "'");
    }
}
