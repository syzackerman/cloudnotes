package com.cloudnotes.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNoteRequest(
        @Schema(example = "Updated project launch checklist") @NotBlank @Size(max = 200) String title,
        @Schema(example = "Updated rollout notes and next steps.") String content) {

    public UpdateNoteRequest {
        title = title == null ? null : title.trim();
    }
}
