package com.avtech.pams.auth;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.setting.SettingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One password rule for every place a password is set: upper case, lower case, a digit and a
 * symbol, at least the configured minimum length (8 by default, set in Settings), and not a
 * commonly breached password or one built on the person's own email name. (Laravel only applied
 * its fixed rule on change-password; admin create/update accepted 6 characters.)
 */
@Component
public class PasswordPolicy {

    /** Passwords that pass the character rules but appear at the top of every breach list. */
    private static final Set<String> FORBIDDEN = Set.of(
            "password1!", "password123!", "p@ssw0rd", "p@ssword1", "p@ssw0rd1", "p@ssw0rd123", "passw0rd!", "qwerty123!",
            "welcome1!", "welcome123!", "admin123!", "admin@123", "letmein1!", "iloveyou1!", "changeme1!", "abc123!@#",
            "summer2026!", "winter2026!", "spring2026!", "pa$$w0rd", "pa$$word1", "test@123", "user@123", "manila123!",
            "philippines1!", "qwerty@123", "1qaz@wsx", "1q2w3e4r!", "zaq1@wsx", "password@1");

    private final SettingService settings;

    public PasswordPolicy(SettingService settings) {
        this.settings = settings;
    }

    public void check(String field, String password, String confirmation) {
        check(field, password, confirmation, null);
    }

    public void check(String field, String password, String confirmation, String email) {
        int min = settings.passwordMinLength();
        if (password == null || password.length() < min) {
            throw ApiException.validation(field, "Password must be at least " + min + " characters.");
        }
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
                || !password.matches(".*[0-9].*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw ApiException.validation(field, "Password must include uppercase, lowercase, a number, and a special character.");
        }
        if (password.length() > 72) {
            throw ApiException.validation(field, "Password must be at most 72 characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (FORBIDDEN.contains(lower)) {
            throw ApiException.validation(field, "That password is on the list of commonly used passwords. Choose another.");
        }
        if (email != null && email.contains("@")) {
            String name = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            List<String> parts = new ArrayList<>(List.of(name.split("[._+-]")));
            parts.add(name);
            for (String part : parts) {
                if (part.length() >= 4 && lower.contains(part)) {
                    throw ApiException.validation(field, "Password must not contain your email name.");
                }
            }
        }
        if (!password.equals(confirmation)) {
            throw ApiException.validation(field, "The password confirmation does not match.");
        }
    }
}
