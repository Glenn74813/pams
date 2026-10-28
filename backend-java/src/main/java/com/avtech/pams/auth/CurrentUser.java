package com.avtech.pams.auth;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Who is calling, and what they may do. Exposed to @PreAuthorize as {@code @perm}:
 * {@code @PreAuthorize("@perm.any('view_users')")}. Super Admin passes every check.
 */
@Component("perm")
public class CurrentUser {

    public AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw ApiException.of(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return p;
    }

    public User user() {
        return principal().user();
    }

    /** True when the caller holds at least one of the given permissions. */
    public boolean any(String... permissions) {
        User u = user();
        return u.isSuperAdmin() || Arrays.stream(permissions).anyMatch(u::hasPermission);
    }

    public boolean superAdmin() {
        return user().isSuperAdmin();
    }
}
