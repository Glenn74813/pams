package com.avtech.pams.auth;

import com.avtech.pams.common.Times;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates "Authorization: Bearer ..." requests. Unlike the Laravel middleware, the token is
 * rejected when it was revoked (logout/refresh) or when its user no longer exists.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokens;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public JwtAuthFilter(JwtService jwtService, RevokedTokenRepository revokedTokens, UserRepository users, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.revokedTokens = revokedTokens;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        JwtService.TokenClaims claims;
        try {
            claims = jwtService.parse(header.substring(7).trim());
        } catch (JwtService.InvalidTokenException e) {
            reject(response, e.getMessage());
            return;
        }
        if (revokedTokens.existsById(claims.jti())) {
            reject(response, "Token has been revoked");
            return;
        }
        Optional<User> user = users.findWithRoleById(claims.userId());
        if (user.isEmpty()) {
            reject(response, "User no longer exists");
            return;
        }

        AuthPrincipal principal = new AuthPrincipal(user.get(), claims.jti(), claims.expiresAt());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        // Who is online (TOR 6.7): at most one write a minute per person.
        LocalDateTime now = Times.now();
        jdbc.update("update users set last_seen_at = ? where id = ? and (last_seen_at is null or last_seen_at < ?)",
                now, principal.user().getId(), now.minusMinutes(1));
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, String status) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":\"" + status + "\"}");
    }
}
