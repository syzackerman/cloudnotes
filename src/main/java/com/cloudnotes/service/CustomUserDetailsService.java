package com.cloudnotes.service;

import com.cloudnotes.domain.User;
import com.cloudnotes.repository.UserRepository;
import com.cloudnotes.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository
                .findByEmailIgnoreCase(AuthService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPasswordHash(), List.of());
    }

    public AuthenticatedUser loadAuthenticatedUser(UUID userId, String email) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!user.getEmail().equals(email)) {
            throw new UsernameNotFoundException("User not found");
        }
        return new AuthenticatedUser(user.getId(), user.getEmail());
    }
}
