package com.avtech.pams.notification;

import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** The caller's own in-app notifications (the bell). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final JdbcTemplate jdbc;
    private final CurrentUser current;

    public NotificationController(JdbcTemplate jdbc, CurrentUser current) {
        this.jdbc = jdbc;
        this.current = current;
    }

    public record NotificationView(Long id, String type, String title, String message, String relatedType, Long relatedId,
                                   boolean isRead, OffsetDateTime createdAt) {
    }

    @GetMapping
    public PageResponse<NotificationView> index(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(name = "per_page", defaultValue = "20") int perPage,
                                                @RequestParam(name = "unread", defaultValue = "false") boolean unreadOnly) {
        int size = Math.max(1, Math.min(perPage, 100));
        int p = Math.max(1, page);
        Long me = current.user().getId();
        String where = " where user_id = ?" + (unreadOnly ? " and is_read = false" : "");
        Long total = jdbc.queryForObject("select count(*) from notifications" + where, Long.class, me);
        List<NotificationView> rows = jdbc.query("select * from notifications" + where + " order by created_at desc, id desc limit ? offset ?",
                (rs, i) -> {
                    Timestamp t = rs.getTimestamp("created_at");
                    return new NotificationView(rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getString("message"),
                            rs.getString("related_type"), (Long) rs.getObject("related_id"), rs.getBoolean("is_read"),
                            t == null ? null : Times.toOffset(t.toLocalDateTime()));
                }, me, size, (p - 1) * size);
        long n = total == null ? 0 : total;
        return new PageResponse<>(rows, new PageResponse.Pagination(n, size, p, Math.max(1, (int) ((n + size - 1) / size))));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", jdbc.queryForObject("select count(*) from notifications where user_id = ? and is_read = false", Long.class,
                current.user().getId()));
    }

    @PutMapping("/{id}/read")
    public Map<String, String> markRead(@PathVariable Long id) {
        int n = jdbc.update("update notifications set is_read = true, updated_at = ? where id = ? and user_id = ?",
                Timestamp.valueOf(Times.now()), id, current.user().getId());
        if (n == 0) throw ApiException.of(HttpStatus.NOT_FOUND, "Notification not found");
        return Map.of("message", "Notification marked as read");
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllRead() {
        int n = jdbc.update("update notifications set is_read = true, updated_at = ? where user_id = ? and is_read = false",
                Timestamp.valueOf(Times.now()), current.user().getId());
        return Map.of("message", "All notifications marked as read", "updated", n);
    }
}
