package com.cloudnotes.dto.attachment;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DownloadUrlResponse(
        @Schema(example = "https://example-presigned-url.invalid/object?signature=placeholder") String url,
        Instant expiresAt) {}
