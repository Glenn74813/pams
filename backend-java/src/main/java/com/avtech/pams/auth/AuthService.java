package com.avtech.pams.auth;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    /** Compared against when the email is unknown, so a miss costs the same time as a wrong password. */
    private final String dummyHash;

    private final UserRepository users;
    private final LoginAttemptRepository attempts;
    private final RevokedTokenRepository revokedTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totp;
    private final int maxAttempts;
    private final int lockoutMinutes;

    public AuthService(UserRepository users, LoginAttemptRepository attempts, RevokedTokenRepository revokedTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, TotpService totp,
                       @Value("${pams.security.max-login-attempts}") int maxAttempts,
                       @Value("${pams.security.lockout-minutes}") int lockoutMinutes) {
        this.users = users;
        this.attempts = attempts;
        this.revokedTokens = revokedTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totp = totp;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    public sealed interface LoginResult permits LoginResult.Success, LoginResult.TwoFactorRequired {
        record Success(User user, JwtService.IssuedToken token) implements LoginResult {
        }

        record TwoFactorRequired() implements LoginResult {
        }
    }

    /** Not transactional on purpose: every attempt row must be kept even when the login is refused. */
    public LoginResult login(String email, String password, String otp, String ip, String userAgent) {
        Optional<User> found = users.findByEmailIgnoreCase(email);
        LocalDateTime windowStart = windowStart(found.orElse(null));

        if (attempts.countFailedSince(email, windowStart) >= maxAttempts) {
            record(email, ip, userAgent, false, "Account locked");
            throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Please try again later.")
                    .with("locked_until", lockedUntil(email, windowStart));
        }

        boolean passwordOk = passwordEncoder.matches(password, found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            record(email, ip, userAgent, false, "Invalid credentials");
            long failed = attempts.countFailedSince(email, windowStart);
            throw ApiException.of(HttpStatus.UNAUTHORIZED, "Invalid credentials")
                    .with("remaining_attempts", Math.max(0, maxAttempts - failed));
        }

        User user = found.get();
        if (!user.isActive()) {
            record(email, ip, userAgent, false, "Account deactivated");
            throw ApiException.of(HttpStatus.FORBIDDEN, "This account has been deactivated. Ask your system administrator.");
        }
        if (user.isTwoFactorEnabled()) {
            if (otp == null || otp.isBlank()) {
                return new LoginResult.TwoFactorRequired();
            }
            if (!totp.verify(user.getTwoFactorSecret(), otp)) {
                record(email, ip, userAgent, false, "Invalid 2FA code");
                throw ApiException.of(HttpStatus.UNAUTHORIZED, "Invalid authenticator code").with("two_factor_required", true);
            }
        }

        record(email, ip, userAgent, true, null);
        return new LoginResult.Success(user, jwtService.issue(user.getId()));
    }

    /** When the user's lockout ends, or null when they are not locked out. */
    public OffsetDateTime lockedUntil(User user) {
        LocalDateTime start = windowStart(user);
        return attempts.countFailedSince(user.getEmail(), start) >= maxAttempts ? lockedUntil(user.getEmail(), start) : null;
    }

    private OffsetDateTime lockedUntil(String email, LocalDateTime windowStart) {
        List<LocalDateTime> recent = attempts.failedTimesSince(email, windowStart, Limit.of(maxAttempts));
        return recent.size() >= maxAttempts ? Times.toOffset(recent.get(maxAttempts - 1).plusMinutes(lockoutMinutes)) : null;
    }

    /** Failures count from the lockout window, or from the moment an administrator last unlocked the account. */
    private LocalDateTime windowStart(User user) {
        LocalDateTime start = Times.now().minusMinutes(lockoutMinutes);
        return user != null && user.getLockoutClearedAt() != null && user.getLockoutClearedAt().isAfter(start) ? user.getLockoutClearedAt() : start;
    }

    public void revoke(AuthPrincipal principal) {
        LocalDateTime expires = LocalDateTime.ofInstant(principal.tokenExpiresAt(), Times.ZONE);
        revokedTokens.save(new RevokedToken(principal.jti(), principal.user().getId(), expires, Times.now()));
    }

    /** New token for the same user; the presented one stops working immediately. */
    public JwtService.IssuedToken refresh(AuthPrincipal principal) {
        revoke(principal);
        return jwtService.issue(principal.user().getId());
    }

    /**
     * Sign-in vouched for by the SSO identity provider (which did the password and any second factor):
     * the account must still be active here, and the attempt is logged like any other.
     */
    public JwtService.IssuedToken ssoLogin(User user, String ip, String userAgent) {
        if (!user.isActive()) {
            record(user.getEmail(), ip, userAgent, false, "Account deactivated (SSO)");
            throw ApiException.of(HttpStatus.FORBIDDEN, "This account has been deactivated. Ask your system administrator.");
        }
        record(user.getEmail(), ip, userAgent, true, null);
        return jwtService.issue(user.getId());
    }

    /** Logs an SSO sign-in that was refused before a PAMS account was found. */
    public void ssoRefused(String email, String ip, String userAgent, String reason) {
        record(email == null ? "(unknown)" : email, ip, userAgent, false, reason);
    }

    private void record(String email, String ip, String userAgent, boolean ok, String reason) {
        attempts.save(new LoginAttempt(email, ip, userAgent, ok, reason));
    }
}
