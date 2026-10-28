package com.avtech.pams.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues and verifies the HS256 access tokens. */
@Service
public class JwtService {

    private static final String ISSUER = "pams";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${pams.jwt.secret}") String secret,
                      @Value("${pams.jwt.ttl-minutes}") long ttlMinutes) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("pams.jwt.secret must be at least 256 bits (base64)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public IssuedToken issue(Long userId) {
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expires, ttl.toSeconds());
    }

    /** Verifies signature, issuer and expiry. */
    public TokenClaims parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return new TokenClaims(Long.valueOf(c.getSubject()), c.getId(), c.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token is Expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Token is Invalid");
        }
    }

    public record IssuedToken(String token, String jti, Instant expiresAt, long expiresInSeconds) {
    }

    public record TokenClaims(Long userId, String jti, Instant expiresAt) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
