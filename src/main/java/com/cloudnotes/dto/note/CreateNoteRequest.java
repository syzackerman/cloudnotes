package com.cloudnotes.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
        @Schema(example = "Project launch checklist") @NotBlank @Size(max = 200) String title,
        @Schema(example = "Draft rollout notes, owners, and follow-up questions.") String content) {

    public CreateNoteRequest {
        title = title == null ? null : title.trim();
    }
}
