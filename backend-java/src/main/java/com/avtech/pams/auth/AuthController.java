package com.avtech.pams.auth;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.user.SignatureStore;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser current;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totp;
    private final SignatureStore signatures;
    private final LoginRateLimiter rateLimiter;

    private static final String USER = "App\\Models\\User";

    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public AuthController(AuthService auth, CurrentUser current, UserRepository users, PasswordEncoder passwordEncoder,
                          TotpService totp, SignatureStore signatures, LoginRateLimiter rateLimiter,
                          PasswordPolicy passwordPolicy, AuditService audit, JdbcTemplate jdbc) {
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.jdbc = jdbc;
        this.auth = auth;
        this.current = current;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.totp = totp;
        this.signatures = signatures;
        this.rateLimiter = rateLimiter;
    }

    public record LoginRequest(@NotBlank(message = "The email is required.") @Email(message = "Enter a valid email address.") String email,
                               @NotBlank(message = "The password is required.") String password, String otp) {
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String ip = ClientIp.of(request);
        if (!rateLimiter.tryAcquire(ip)) {
            throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, "Too many login requests. Please wait a minute.");
        }
        var result = auth.login(body.email().trim(), body.password(), body.otp(), ip, request.getHeader("User-Agent"));
        if (result instanceof AuthService.LoginResult.Success ok) {
            return tokenResponse(ok.token(), ok.user());
        }
        Map<String, Object> twoFactor = new LinkedHashMap<>();
        twoFactor.put("two_factor_required", true);
        twoFactor.put("message", "Enter the 6-digit code from your authenticator app.");
        return twoFactor;
    }

    public record LogoutRequest(String reason) {
    }

    /** Sign-out is recorded (TOR 6.3), including the automatic one after the configured idle time. */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) LogoutRequest body) {
        User u = current.user();
        boolean idle = body != null && "idle".equals(body.reason());
        audit.record("logged_out", USER, u.getId(),
                u.getName() + (idle ? " was signed out after the idle time limit" : " signed out"), null, null);
        jdbc.update("update users set last_seen_at = null where id = ?", u.getId());
        auth.revoke(current.principal());
        return Map.of("message", "User successfully signed out");
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        AuthPrincipal p = current.principal();
        return tokenResponse(auth.refresh(p), p.user());
    }

    @GetMapping("/me")
    public AuthUserView me() {
        User u = current.user();
        return AuthUserView.of(u, signatures.dataUri(u.getSignaturePath()), passwordPolicy.expired(u));
    }

    public record ChangePasswordRequest(@NotBlank(message = "Enter your current password.") String currentPassword, String password, String passwordConfirmation) {
    }

    @PostMapping("/change-password")
    @Transactional
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        User u = reload();
        if (!passwordEncoder.matches(body.currentPassword(), u.getPasswordHash())) {
            throw ApiException.validation("current_password", "Current password is incorrect.");
        }
        passwordPolicy.check("password", body.password(), body.passwordConfirmation(), u.getEmail());
        if (passwordEncoder.matches(body.password(), u.getPasswordHash())) {
            throw ApiException.validation("password", "Choose a new password, different from the current one.");
        }
        u.setPasswordHash(passwordEncoder.encode(body.password()));
        u.setPasswordChangedAt(Times.now());
        audit.record("password_changed", USER, u.getId(), u.getName() + " changed their password", null, null);
        return Map.of("message", "Password changed successfully.");
    }

    @PostMapping("/signature")
    @Transactional
    public Map<String, Object> uploadSignature(@RequestParam("signature") MultipartFile file) {
        User u = reload();
        String path = signatures.save(u.getId(), file);
        signatures.delete(u.getSignaturePath());
        u.setSignaturePath(path);
        audit.record("updated", USER, u.getId(), u.getName() + " uploaded a signature", null, Map.of("has_signature", true));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Signature uploaded.");
        res.put("has_signature", true);
        res.put("signature_image", signatures.dataUri(path));
        return res;
    }

    @DeleteMapping("/signature")
    @Transactional
    public Map<String, Object> deleteSignature() {
        User u = reload();
        signatures.delete(u.getSignaturePath());
        u.setSignaturePath(null);
        audit.record("updated", USER, u.getId(), u.getName() + " removed their signature", null, Map.of("has_signature", false));
        return Map.of("message", "Signature removed.", "has_signature", false);
    }

    @PostMapping("/2fa/setup")
    @Transactional
    public Map<String, Object> setupTwoFactor() {
        User u = reload();
        if (u.isTwoFactorEnabled()) {
            throw ApiException.of(HttpStatus.CONFLICT, "Two-factor authentication is already enabled. Disable it first.");
        }
        String secret = totp.generateSecret();
        u.setTwoFactorSecret(secret);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("secret", secret);
        res.put("otpauth_uri", totp.otpauthUri(secret, u.getEmail()));
        res.put("message", "Scan the QR / enter the key in your authenticator app, then confirm with a code to enable.");
        return res;
    }

    public record OtpRequest(@NotBlank(message = "Enter the 6-digit code.") String otp) {
    }

    @PostMapping("/2fa/enable")
    @Transactional
    public Map<String, Object> enableTwoFactor(@Valid @RequestBody OtpRequest body) {
        User u = reload();
        if (u.getTwoFactorSecret() == null) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "Start 2FA setup first.");
        }
        if (!totp.verify(u.getTwoFactorSecret(), body.otp())) {
            throw ApiException.validation("otp", "Invalid code. Please try again.");
        }
        u.setTwoFactorEnabled(true);
        audit.record("updated", USER, u.getId(), u.getName() + " turned on two-factor authentication",
                Map.of("two_factor_enabled", false), Map.of("two_factor_enabled", true));
        return Map.of("message", "Two-factor authentication enabled.");
    }

    public record PasswordConfirmRequest(@NotBlank(message = "Enter your password.") String password) {
    }

    @PostMapping("/2fa/disable")
    @Transactional
    public Map<String, Object> disableTwoFactor(@Valid @RequestBody PasswordConfirmRequest body) {
        User u = reload();
        if (!passwordEncoder.matches(body.password(), u.getPasswordHash())) {
            throw ApiException.validation("password", "Password is incorrect.");
        }
        u.setTwoFactorEnabled(false);
        u.setTwoFactorSecret(null);
        audit.record("updated", USER, u.getId(), u.getName() + " turned off two-factor authentication",
                Map.of("two_factor_enabled", true), Map.of("two_factor_enabled", false));
        return Map.of("message", "Two-factor authentication disabled.");
    }

    /** The principal's user is detached; changes go through a managed copy. */
    private User reload() {
        return users.findById(current.user().getId()).orElseThrow(() -> ApiException.notFound("User"));
    }

    Map<String, Object> tokenResponse(JwtService.IssuedToken token, User user) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("access_token", token.token());
        res.put("token_type", "bearer");
        res.put("expires_in", token.expiresInSeconds());
        res.put("user", AuthUserView.of(user, null, passwordPolicy.expired(user)));
        return res;
    }
}
