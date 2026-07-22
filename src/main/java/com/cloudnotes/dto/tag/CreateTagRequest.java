package com.cloudnotes.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(@Schema(example = "Work") @NotBlank @Size(max = 80) String name) {

    public CreateTagRequest {
        name = name == null ? null : name.trim();
    }
}
