package com.cloudnotes.service;

import com.cloudnotes.domain.User;
import com.cloudnotes.dto.auth.AuthenticationResponse;
import com.cloudnotes.dto.auth.CurrentUserResponse;
import com.cloudnotes.dto.auth.LoginRequest;
import com.cloudnotes.dto.auth.RegisterRequest;
import com.cloudnotes.exception.DuplicateEmailException;
import com.cloudnotes.exception.InvalidCredentialsException;
import com.cloudnotes.repository.UserRepository;
import com.cloudnotes.security.AuthenticatedUser;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationMetrics metrics;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ApplicationMetrics metrics) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.metrics = metrics;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        User user = User.builder()
                .email(normalizedEmail)
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        try {
            User savedUser = userRepository.save(user);
            metrics.authSuccess();
            return AuthenticationResponse.from(savedUser, jwtService.generateToken(savedUser));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseThrow(() -> {
            metrics.authFailure();
            return new InvalidCredentialsException();
        });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            metrics.authFailure();
            throw new InvalidCredentialsException();
        }
        metrics.authSuccess();
        return AuthenticationResponse.from(user, jwtService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(AuthenticatedUser authenticatedUser) {
        User user = userRepository.findById(authenticatedUser.id()).orElseThrow(InvalidCredentialsException::new);
        return CurrentUserResponse.from(user);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
