package com.avtech.pams.notification;

import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-app notifications, written in the Laravel format so the existing notification bell shows them.
 * Never throws: a failed notification must not undo the change that triggered it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcTemplate jdbc;
    private final Db db;
    private final ObjectMapper json;

    public NotificationService(JdbcTemplate jdbc, Db db, ObjectMapper json) {
        this.jdbc = jdbc;
        this.db = db;
        this.json = json;
    }

    public void notifyUser(Long userId, String type, String title, String message, String relatedType, Long relatedId) {
        if (userId == null) return;
        try {
            Timestamp now = Timestamp.valueOf(Times.now());
            jdbc.update("""
                    insert into notifications (user_id, type, title, message, related_type, related_id, is_read, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, """ + db.f() + ", ?, ?)", userId, type, title, message, relatedType, relatedId, now, now);
        } catch (RuntimeException e) {
            log.warn("Could not notify user {}: {}", userId, e.getMessage());
        }
    }

    /** Everyone whose role grants any of the permissions (Super Admins included), except one user. */
    public void notifyPermission(List<String> permissions, Long exceptUserId, String type, String title, String message,
                                 String relatedType, Long relatedId) {
        try {
            List<Long> users = new ArrayList<>();
            for (Map<String, Object> r : jdbc.queryForList("select u.id, r.name, r.permissions from users u join roles r on r.id = u.role_id where u.is_active = ? order by u.id", true)) {
                if ("Super Admin".equals(r.get("name")) || grants(r.get("permissions") == null ? null : r.get("permissions").toString(), permissions)) {
                    users.add(((Number) r.get("id")).longValue());
                }
            }
            users.stream().filter(id -> !id.equals(exceptUserId))
                    .forEach(id -> notifyUser(id, type, title, message, relatedType, relatedId));
        } catch (RuntimeException e) {
            log.warn("Could not send '{}' notifications: {}", type, e.getMessage());
        }
    }

    /** True when the role's JSON permission list holds "*" or any of the given permissions. */
    private boolean grants(String permissionsJson, List<String> wanted) {
        if (permissionsJson == null || permissionsJson.isBlank()) return false;
        for (var p : json.readTree(permissionsJson)) {
            String perm = p.asString();
            if ("*".equals(perm) || wanted.contains(perm)) return true;
        }
        return false;
    }
}
