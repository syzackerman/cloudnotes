package com.cloudnotes.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FavoriteNoteRequest(@Schema(example = "true") @NotNull Boolean favorite) {}
