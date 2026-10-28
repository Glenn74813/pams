package com.avtech.pams.auth;

import com.avtech.pams.user.User;

import java.time.Instant;

/** The signed-in user (with role and department loaded) plus the token that authenticated this request. */
public record AuthPrincipal(User user, String jti, Instant tokenExpiresAt) {
}
