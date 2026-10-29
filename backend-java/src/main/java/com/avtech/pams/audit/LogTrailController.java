package com.avtech.pams.audit;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.setting.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Log trails: stock-card movements, sign-in activity, and who is using the system now (TOR 6.3, 6.7). */
@RestController
@RequestMapping("/api/logs")
@PreAuthorize("@perm.any('view_log_trails')")
public class LogTrailController {

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final Db db;

    public LogTrailController(JdbcTemplate jdbc, SettingService settings, Db db) {
        this.jdbc = jdbc;
        this.db = db;
        this.settings = settings;
    }

    public record Ref(Long id, String name) {
    }

    public record ItemRef(Long id, String code, String name) {
    }

    public record InventoryEntry(Long id, ItemRef item, Ref user, String action, Integer quantity, Integer previousQuantity,
                                 Integer newQuantity, String referenceType, Long referenceId, String notes, OffsetDateTime createdAt) {
    }

    @GetMapping("/inventory")
    public PageResponse<InventoryEntry> inventory(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(required = false) String action,
                                                  @RequestParam(name = "item_id", required = false) Long itemId,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(name = "start_date", required = false) String start,
                                                  @RequestParam(name = "end_date", required = false) String end) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (blank(action) != null) {
            w.append(" and l.action = ?");
            args.add(action.trim());
        }
        if (itemId != null) {
            w.append(" and l.item_id = ?");
            args.add(itemId);
        }
        if (blank(search) != null) {
            w.append(" and (lower(i.name) like lower(?) or lower(i.code) like lower(?) or lower(l.notes) like lower(?) or lower(u.name) like lower(?))");
            for (int k = 0; k < 4; k++) args.add("%" + search.trim() + "%");
        }
        range(w, args, "l.created_at", start, end);
        RowMapper<InventoryEntry> map = (rs, i) -> new InventoryEntry(rs.getLong(1),
                rs.getObject(2) == null ? null : new ItemRef(rs.getLong(2), rs.getString(3), rs.getString(4)),
                rs.getObject(5) == null ? null : new Ref(rs.getLong(5), rs.getString(6)),
                rs.getString(7), rs.getObject(8, Integer.class), rs.getObject(9, Integer.class), rs.getObject(10, Integer.class),
                shortType(rs.getString(11)), rs.getObject(12, Long.class), rs.getString(13), offset(rs.getTimestamp(14)));
        return page("""
                select l.id, i.id, i.code, i.name, u.id, u.name, l.action, l.quantity, l.previous_quantity, l.new_quantity,
                       l.reference_type, l.reference_id, l.notes, l.created_at
                from inventory_logs l left join items i on i.id = l.item_id left join users u on u.id = l.user_id""",
                "inventory_logs l left join items i on i.id = l.item_id left join users u on u.id = l.user_id",
                w.toString(), "l.created_at desc, l.id desc", args, page, map);
    }

    @GetMapping("/inventory/actions")
    public List<String> inventoryActions() {
        return jdbc.queryForList("select distinct action from inventory_logs order by action", String.class);
    }

    public record LoginEntry(Long id, String email, String ipAddress, String userAgent, boolean successful, String failureReason,
                             OffsetDateTime createdAt) {
    }

    @GetMapping("/login-attempts")
    public PageResponse<LoginEntry> loginAttempts(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(required = false) String email,
                                                  @RequestParam(required = false) String successful,
                                                  @RequestParam(name = "start_date", required = false) String start,
                                                  @RequestParam(name = "end_date", required = false) String end) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (blank(email) != null) {
            w.append(" and lower(a.email) like lower(?)");
            args.add("%" + email.trim() + "%");
        }
        if (blank(successful) != null) {
            w.append(" and a.successful = ?");
            args.add("true".equals(successful) || "1".equals(successful));
        }
        range(w, args, "a.created_at", start, end);
        return page("select a.id, a.email, a.ip_address, a.user_agent, a.successful, a.failure_reason, a.created_at from login_attempts a",
                "login_attempts a", w.toString(), "a.created_at desc, a.id desc", args, page,
                (rs, i) -> new LoginEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        rs.getString(6), offset(rs.getTimestamp(7))));
    }

    public record ActiveUser(Long id, String name, String email, String role, String department, OffsetDateTime lastSeenAt,
                             OffsetDateTime signedInAt, String ipAddress) {
    }

    /**
     * People with activity inside the idle window (or the last 15 minutes when idle sign-out is off),
     * with their latest successful sign-in. Sign-out clears a person's activity at once.
     */
    @GetMapping("/active-users")
    public Map<String, Object> activeUsers() {
        int idle = settings.idleTimeoutMinutes();
        int window = idle > 0 ? idle : 15;
        LocalDateTime since = Times.now().minusMinutes(window);
        List<ActiveUser> users = jdbc.query("""
                select u.id, u.name, u.email, r.name, d.name, u.last_seen_at, s.created_at, s.ip_address
                from users u join roles r on r.id = u.role_id left join departments d on d.id = u.department_id
                left join (select lower(email) email, created_at, ip_address,
                                  row_number() over (partition by lower(email) order by created_at desc, id desc) rn
                           from login_attempts where successful = """ + db.t() + " " + """
                ) s on s.email = lower(u.email) and s.rn = 1
                where u.last_seen_at >= ? order by u.last_seen_at desc""", (rs, i) -> new ActiveUser(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                offset(rs.getTimestamp(6)), offset(rs.getTimestamp(7)), rs.getString(8)), since);
        return Map.of("window_minutes", window, "users", users);
    }

    // ---------------------------------------------------------------- helpers

    private <T> PageResponse<T> page(String select, String from, String where, String order, List<Object> args, int page, RowMapper<T> map) {
        int size = 20;
        int p = Math.max(1, page);
        Long total = jdbc.queryForObject("select count(*) from " + from + where, Long.class, args.toArray());
        List<Object> all = new ArrayList<>(args);
        all.add((long) (p - 1) * size);
        all.add(size);
        List<T> rows = jdbc.query(select + where + " order by " + order + " offset ? rows fetch next ? rows only", map, all.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PageResponse.Pagination(t, size, p, (int) Math.max(1, (t + size - 1) / size)));
    }

    private static void range(StringBuilder w, List<Object> args, String column, String start, String end) {
        LocalDate from = date("start_date", start);
        LocalDate to = date("end_date", end);
        if (from != null) {
            w.append(" and ").append(column).append(" >= ?");
            args.add(from.atStartOfDay());
        }
        if (to != null) {
            w.append(" and ").append(column).append(" < ?");
            args.add(to.plusDays(1).atStartOfDay());
        }
    }

    private static OffsetDateTime offset(java.sql.Timestamp t) {
        return t == null ? null : Times.toOffset(t.toLocalDateTime());
    }

    private static String shortType(String t) {
        return t == null ? null : t.substring(t.lastIndexOf('\\') + 1);
    }

    private static LocalDate date(String field, String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (RuntimeException e) {
            throw ApiException.validation(field, "Use a date like 2026-10-29.");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
