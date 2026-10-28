package com.avtech.pams.notification;

import com.avtech.pams.common.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * In-app notifications, written in the Laravel format so the existing notification bell shows them.
 * Never throws: a failed notification must not undo the change that triggered it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void notifyUser(Long userId, String type, String title, String message, String relatedType, Long relatedId) {
        if (userId == null) return;
        try {
            Timestamp now = Timestamp.valueOf(Times.now());
            jdbc.update("""
                    insert into notifications (user_id, type, title, message, related_type, related_id, is_read, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, false, ?, ?)""", userId, type, title, message, relatedType, relatedId, now, now);
        } catch (RuntimeException e) {
            log.warn("Could not notify user {}: {}", userId, e.getMessage());
        }
    }

    /** Everyone whose role grants any of the permissions (Super Admins included), except one user. */
    public void notifyPermission(List<String> permissions, Long exceptUserId, String type, String title, String message,
                                 String relatedType, Long relatedId) {
        try {
            String any = String.join(",", permissions.stream().map(p -> "?").toList());
            List<Long> users = jdbc.queryForList("""
                            select distinct u.id from users u join roles r on r.id = u.role_id, json_array_elements_text(r.permissions) p(perm)
                            where p.perm = '*' or r.name = 'Super Admin' or p.perm in (""" + any + ")",
                    Long.class, permissions.toArray());
            users.stream().filter(id -> !id.equals(exceptUserId))
                    .forEach(id -> notifyUser(id, type, title, message, relatedType, relatedId));
        } catch (RuntimeException e) {
            log.warn("Could not send '{}' notifications: {}", type, e.getMessage());
        }
    }
}
