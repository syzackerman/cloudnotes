package com.cloudnotes.controller;

import com.cloudnotes.dto.auth.AuthenticationResponse;
import com.cloudnotes.dto.auth.LoginRequest;
import com.cloudnotes.dto.auth.RegisterRequest;
import com.cloudnotes.service.AuthService;
import com.cloudnotes.service.RateLimiterService;
import com.cloudnotes.web.ClientIpAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register and log in to receive JWT access tokens")
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    public AuthController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user", description = "Public endpoint. Returns a JWT for the new account.")
    AuthenticationResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        rateLimiterService.checkRegistration(ClientIpAddress.from(servletRequest));
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Public endpoint. Returns a JWT for valid credentials.")
    AuthenticationResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        rateLimiterService.checkLogin(ClientIpAddress.from(servletRequest));
        return authService.login(request);
    }
}
