package com.cloudnotes.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors) {}
