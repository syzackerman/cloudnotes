package com.cloudnotes.dto.note;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpdateNoteTagsRequest(
        @ArraySchema(schema = @Schema(example = "22222222-2222-2222-2222-222222222222")) @NotNull List<@NotNull UUID> tagIds) {}
