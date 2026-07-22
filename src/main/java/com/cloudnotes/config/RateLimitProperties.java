package com.cloudnotes.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudnotes.rate-limit")
public record RateLimitProperties(boolean enabled, Limit login, Limit registration, Limit upload, Limit downloadUrl) {

    public RateLimitProperties {
        login = login == null ? new Limit(10, Duration.ofMinutes(1)) : login;
        registration = registration == null ? new Limit(5, Duration.ofHours(1)) : registration;
        upload = upload == null ? new Limit(30, Duration.ofHours(1)) : upload;
        downloadUrl = downloadUrl == null ? new Limit(60, Duration.ofMinutes(1)) : downloadUrl;
    }

    public record Limit(long capacity, Duration window) {
        public Limit {
            if (capacity < 1) {
                capacity = 1;
            }
            if (window == null || window.isNegative() || window.isZero()) {
                window = Duration.ofMinutes(1);
            }
        }
    }
}
