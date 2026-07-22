package com.cloudnotes.dto.note;

import com.cloudnotes.domain.Note;
import com.cloudnotes.dto.tag.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
        @Schema(example = "33333333-3333-3333-3333-333333333333") UUID id,
        @Schema(example = "Project launch checklist") String title,
        @Schema(example = "Draft rollout notes, owners, and follow-up questions.") String content,
        @Schema(example = "true") boolean favorite,
        List<TagResponse> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.isFavorite(),
                note.getTags().stream()
                        .map(TagResponse::from)
                        .sorted(Comparator.comparing(TagResponse::name, String.CASE_INSENSITIVE_ORDER))
                        .toList(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
