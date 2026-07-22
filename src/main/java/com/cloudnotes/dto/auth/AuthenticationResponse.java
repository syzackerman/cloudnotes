package com.cloudnotes.dto.auth;

import com.cloudnotes.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record AuthenticationResponse(
        @Schema(example = "11111111-1111-1111-1111-111111111111") UUID userId,
        @Schema(example = "reader@example.com") String email,
        @Schema(example = "Reader One") String displayName,
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.placeholder.signature") String token) {

    public static AuthenticationResponse from(User user, String token) {
        return new AuthenticationResponse(user.getId(), user.getEmail(), user.getDisplayName(), token);
    }
}
