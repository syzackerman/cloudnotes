package com.cloudnotes.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "cloudnotes.storage")
public record StorageProperties(String region, String bucket, Duration downloadUrlExpiration, DataSize maxFileSize) {}
