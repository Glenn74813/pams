package com.avtech.pams.auth;

import com.avtech.pams.common.ApiException;

/**
 * One password rule for every place a password is set: at least 8 characters with upper case,
 * lower case, a digit and a symbol. (Laravel only applied this on change-password; admin
 * create/update accepted 6 characters.)
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void check(String field, String password, String confirmation) {
        if (password == null || password.length() < 8) {
            throw ApiException.validation(field, "Password must be at least 8 characters.");
        }
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
                || !password.matches(".*[0-9].*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw ApiException.validation(field, "Password must include uppercase, lowercase, a number, and a special character.");
        }
        if (password.length() > 72) {
            throw ApiException.validation(field, "Password must be at most 72 characters.");
        }
        if (!password.equals(confirmation)) {
            throw ApiException.validation(field, "The password confirmation does not match.");
        }
    }
}
