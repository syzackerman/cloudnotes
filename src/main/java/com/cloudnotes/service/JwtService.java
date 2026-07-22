package com.cloudnotes.service;

import com.cloudnotes.config.JwtProperties;
import com.cloudnotes.domain.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public JwtService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void validateConfiguration() {
        requireConfiguredSecret();
    }

    public String generateToken(User user) {
        requireConfiguredSecret();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.expiration());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId().toString());
        payload.put("userId", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public JwtClaims validateToken(String token) {
        requireConfiguredSecret();
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidJwtException();
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidJwtException();
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        String userId = claimAsString(payload, "userId");
        String email = claimAsString(payload, "email");
        long expiresAtEpochSecond = claimAsLong(payload, "exp");
        Instant expiresAt = Instant.ofEpochSecond(expiresAtEpochSecond);
        if (!expiresAt.isAfter(Instant.now())) {
            throw new InvalidJwtException();
        }

        return new JwtClaims(UUID.fromString(userId), email, expiresAt);
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new InvalidJwtException();
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return objectMapper.readValue(BASE64_URL_DECODER.decode(value), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new InvalidJwtException();
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new InvalidJwtException();
        }
    }

    private void requireConfiguredSecret() {
        if (jwtProperties.secret() == null || jwtProperties.secret().isBlank()) {
            throw new JwtConfigurationException("JWT_SECRET must be configured");
        }
        if (jwtProperties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new JwtConfigurationException("JWT_SECRET must be at least 32 bytes");
        }
        if (jwtProperties.expiration() == null
                || jwtProperties.expiration().isNegative()
                || jwtProperties.expiration().isZero()) {
            throw new JwtConfigurationException("JWT_EXPIRATION must be a positive duration");
        }
    }

    private String claimAsString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new InvalidJwtException();
    }

    private long claimAsLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        throw new InvalidJwtException();
    }

    public record JwtClaims(UUID userId, String email, Instant expiresAt) {}

    public static class InvalidJwtException extends RuntimeException {}

    public static class JwtConfigurationException extends IllegalStateException {

        public JwtConfigurationException(String message) {
            super(message);
        }
    }
}
