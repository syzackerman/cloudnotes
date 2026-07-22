package com.cloudnotes.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequest(
        @Schema(example = "reader@example.com") @NotBlank @Email @Size(max = 320) String email,
        @Schema(example = "correct-horse-battery") @NotBlank @Size(max = 128) String password) {

    public LoginRequest {
        email = normalizeEmail(email);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
