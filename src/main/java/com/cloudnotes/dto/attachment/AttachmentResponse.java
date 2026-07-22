package com.cloudnotes.dto.attachment;

import com.cloudnotes.domain.Attachment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        @Schema(example = "44444444-4444-4444-4444-444444444444") UUID id,
        @Schema(example = "33333333-3333-3333-3333-333333333333") UUID noteId,
        @Schema(example = "requirements.pdf") String originalFilename,
        @Schema(example = "application/pdf") String contentType,
        @Schema(example = "204800") long sizeBytes,
        Instant createdAt) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getNote().getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }
}
