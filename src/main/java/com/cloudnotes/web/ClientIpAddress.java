package com.cloudnotes.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpAddress {

    private ClientIpAddress() {}

    public static String from(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
