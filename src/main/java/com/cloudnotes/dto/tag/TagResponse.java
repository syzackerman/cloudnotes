package com.cloudnotes.dto.tag;

import com.cloudnotes.domain.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record TagResponse(
        @Schema(example = "22222222-2222-2222-2222-222222222222") UUID id, @Schema(example = "Work") String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
