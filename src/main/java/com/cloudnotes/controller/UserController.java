package com.cloudnotes.controller;

import com.cloudnotes.config.OpenApiConfig;
import com.cloudnotes.dto.auth.CurrentUserResponse;
import com.cloudnotes.security.AuthenticatedUser;
import com.cloudnotes.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Authenticated user profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return authService.currentUser(authenticatedUser);
    }
}
