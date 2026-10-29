package com.avtech.pams.auth;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /** Attempts refused because the account was already locked are logged but do not extend the lock. */
    @Query("select count(a) from LoginAttempt a where lower(a.email) = lower(:email) and a.successful = false and a.createdAt >= :since"
            + " and (a.failureReason is null or a.failureReason <> 'Account locked')")
    long countFailedSince(String email, LocalDateTime since);

    /** Failed-attempt times in the window, newest first; the lockout ends when the Nth one ages out. */
    @Query("select a.createdAt from LoginAttempt a where lower(a.email) = lower(:email) and a.successful = false and a.createdAt >= :since"
            + " and (a.failureReason is null or a.failureReason <> 'Account locked') order by a.createdAt desc")
    List<LocalDateTime> failedTimesSince(String email, LocalDateTime since, Limit limit);
}
