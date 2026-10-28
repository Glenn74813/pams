package com.avtech.pams.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * The caller's address. X-Forwarded-For is only believed when the direct peer is this machine
     * (the dev proxy or a local reverse proxy); otherwise any client could spoof it.
     */
    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        boolean fromLocalProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (fromLocalProxy && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return remote;
    }
}
