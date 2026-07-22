package com.cloudnotes.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudnotes.cors")
public record CorsProperties(List<String> allowedOrigins) {}
