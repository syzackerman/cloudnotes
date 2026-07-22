package com.cloudnotes.dto.auth;

import com.cloudnotes.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record CurrentUserResponse(
        @Schema(example = "11111111-1111-1111-1111-111111111111") UUID id,
        @Schema(example = "reader@example.com") String email,
        @Schema(example = "Reader One") String displayName) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
