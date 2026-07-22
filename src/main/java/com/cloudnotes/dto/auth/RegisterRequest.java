package com.cloudnotes.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @Schema(example = "reader@example.com") @NotBlank @Email @Size(max = 320) String email,
        @Schema(example = "Reader One") @NotBlank @Size(max = 120) String displayName,
        @Schema(example = "correct-horse-battery") @NotBlank @Size(min = 8, max = 128) String password) {

    public RegisterRequest {
        email = normalizeEmail(email);
        displayName = displayName == null ? null : displayName.trim();
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
