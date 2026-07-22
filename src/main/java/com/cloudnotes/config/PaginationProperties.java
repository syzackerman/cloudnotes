package com.cloudnotes.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudnotes.pagination")
public record PaginationProperties(int defaultSize, int maxSize, Set<String> allowedSortFields) {

    public PaginationProperties {
        if (defaultSize < 1) {
            defaultSize = 20;
        }
        if (maxSize < 1) {
            maxSize = 100;
        }
        allowedSortFields = allowedSortFields == null || allowedSortFields.isEmpty()
                ? Set.of("createdAt", "updatedAt", "title")
                : Set.copyOf(allowedSortFields);
    }
}
