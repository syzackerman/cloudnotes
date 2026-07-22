package com.cloudnotes.service;

import com.cloudnotes.config.RateLimitProperties;
import com.cloudnotes.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterService(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RateLimiterService(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void checkLogin(String clientKey) {
        check("login", clientKey, properties.login());
    }

    public void checkRegistration(String clientKey) {
        check("registration", clientKey, properties.registration());
    }

    public void checkUpload(String userKey) {
        check("upload", userKey, properties.upload());
    }

    public void checkDownloadUrl(String userKey) {
        check("download-url", userKey, properties.downloadUrl());
    }

    private void check(String bucket, String key, RateLimitProperties.Limit limit) {
        if (!properties.enabled()) {
            return;
        }
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        String mapKey = bucket + ":" + safeKey;
        Instant now = clock.instant();
        Window window = windows.compute(mapKey, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new Window(1, now.plus(limit.window()));
            }
            return new Window(existing.count() + 1, existing.expiresAt());
        });
        if (window.count() > limit.capacity()) {
            throw new RateLimitExceededException(Duration.between(now, window.expiresAt()));
        }
    }

    private record Window(long count, Instant expiresAt) {}
}
