package com.avtech.pams.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP limit on login requests (the old "throttle:5,1"), on top of the per-account lockout.
 * In-memory: fine for a single application server.
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_MS = 60_000;

    private final int maxPerMinute;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${pams.security.login-requests-per-minute}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    /** Records a request from this IP and returns false when it is over the limit. */
    public boolean tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= maxPerMinute) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
