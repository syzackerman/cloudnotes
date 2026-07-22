package com.cloudnotes.config;

import com.cloudnotes.storage.FileStorageService;
import com.cloudnotes.storage.S3FileStorageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties storageProperties) {
        return S3Client.builder()
                .region(region(storageProperties))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties storageProperties) {
        return S3Presigner.builder()
                .region(region(storageProperties))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    FileStorageService fileStorageService(
            S3Client s3Client, S3Presigner s3Presigner, StorageProperties storageProperties) {
        return new S3FileStorageService(s3Client, s3Presigner, storageProperties);
    }

    private Region region(StorageProperties storageProperties) {
        String region = storageProperties.region();
        if (region == null || region.isBlank()) {
            return Region.US_EAST_1;
        }
        return Region.of(region);
    }
}
