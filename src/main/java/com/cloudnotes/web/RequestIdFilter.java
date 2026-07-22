package com.cloudnotes.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String ATTRIBUTE_REQUEST_ID = RequestIdFilter.class.getName() + ".requestId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_HTTP_METHOD = "httpMethod";
    public static final String MDC_REQUEST_PATH = "requestPath";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = safeRequestId(request.getHeader(HEADER_REQUEST_ID));
        request.setAttribute(ATTRIBUTE_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_REQUEST_PATH, sanitizeForLog(request.getRequestURI()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_PATH);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    public static String currentRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE_REQUEST_ID);
        return requestId instanceof String value ? value : null;
    }

    private String safeRequestId(String incomingRequestId) {
        if (incomingRequestId != null
                && SAFE_REQUEST_ID.matcher(incomingRequestId).matches()) {
            return incomingRequestId;
        }
        return UUID.randomUUID().toString();
    }

    private String sanitizeForLog(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", "_");
    }
}
