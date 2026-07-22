package com.cloudnotes.exception;

import com.cloudnotes.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseFactory {

    public ErrorResponse create(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        String requestId =
                request == null ? MDC.get(RequestIdFilter.MDC_REQUEST_ID) : RequestIdFilter.currentRequestId(request);
        String path = request == null ? null : request.getRequestURI();
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                requestId,
                fieldErrors == null || fieldErrors.isEmpty() ? null : Map.copyOf(fieldErrors));
    }
}
