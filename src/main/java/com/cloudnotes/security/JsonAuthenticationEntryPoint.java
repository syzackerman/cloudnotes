package com.cloudnotes.security;

import com.cloudnotes.exception.ErrorCode;
import com.cloudnotes.exception.ErrorResponse;
import com.cloudnotes.exception.ErrorResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ErrorResponseFactory errorResponseFactory, ObjectMapper objectMapper) {
        this.errorResponseFactory = errorResponseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writeUnauthorized(request, response);
    }

    public void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponse body = errorResponseFactory.create(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_TOKEN,
                "Authentication is required or the token is invalid",
                request,
                null);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
