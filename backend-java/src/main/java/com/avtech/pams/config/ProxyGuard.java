package com.avtech.pams.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * PAMS published through Cloudflare (cloudflare/worker.js in front of a Cloudflare Tunnel). When
 * pams.proxy.shared-secret is set, a request that came through Cloudflare (it carries CF-Ray) must also carry
 * that secret in X-PAMS-Proxy-Secret, which only the Worker sends, so the tunnel address cannot be called
 * directly. For those requests the caller's address is the one the Worker passes in X-PAMS-Client-IP, so login
 * limits and the audit trail see the real user rather than the tunnel. Requests on the local network (no
 * CF-Ray) are not affected, and nothing changes while the secret is unset.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProxyGuard extends OncePerRequestFilter {

    static final String SECRET_HEADER = "X-PAMS-Proxy-Secret";
    static final String CLIENT_IP_HEADER = "X-PAMS-Client-IP";

    private final byte[] secret;

    public ProxyGuard(@Value("${pams.proxy.shared-secret:}") String secret) {
        this.secret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (secret == null || request.getHeader("CF-Ray") == null) {
            chain.doFilter(request, response);
            return;
        }
        String given = request.getHeader(SECRET_HEADER);
        if (given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"This address is only reachable through the PAMS web application.\"}");
            return;
        }
        String ip = request.getHeader(CLIENT_IP_HEADER);
        if (ip == null || !ip.matches("[0-9A-Fa-f:.]{2,45}")) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return ip;
            }

            @Override
            public String getHeader(String name) {
                // The address above is the caller's; a forwarded chain added on the way is not.
                return "X-Forwarded-For".equalsIgnoreCase(name) ? null : super.getHeader(name);
            }
        }, response);
    }
}
