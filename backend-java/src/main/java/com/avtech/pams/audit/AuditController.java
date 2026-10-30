package com.avtech.pams.audit;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The system-wide audit trail (TOR General 6, Audit & Compliance): filterable list, full detail
 * with before/after values, CSV export, and a way for the client to record prints and downloads.
 * Laravel exported Excel only and never logged printing or downloading.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> CLIENT_EVENTS = Set.of("printed", "downloaded");
    private static final int EXPORT_LIMIT = 10_000;

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AuditController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record Ref(Long id, String name) {
    }

    public record Entry(Long id, Ref user, String userName, String userRole, String action, String auditableType,
                        Long auditableId, String description, String ipAddress, OffsetDateTime createdAt,
                        JsonNode oldValues, JsonNode newValues, String userAgent, String url, String method) {
    }

    public record Filter(Long userId, String action, String type, String search, LocalDate from, LocalDate to) {
        String where(List<Object> args) {
            StringBuilder w = new StringBuilder(" where 1 = 1");
            if (userId != null) {
                w.append(" and a.user_id = ?");
                args.add(userId);
            }
            if (action != null) {
                w.append(" and a.action = ?");
                args.add(action);
            }
            if (type != null) {
                w.append(" and (a.auditable_type = ? or a.auditable_type = ?)");
                args.add(type);
                args.add("App\\Models\\" + type);
            }
            if (search != null) {
                w.append(" and (lower(a.description) like lower(?) or lower(a.user_name) like lower(?))");
                args.add("%" + search + "%");
                args.add("%" + search + "%");
            }
            if (from != null) {
                w.append(" and a.created_at >= ?");
                args.add(from.atStartOfDay());
            }
            if (to != null) {
                w.append(" and a.created_at < ?");
                args.add(to.plusDays(1).atStartOfDay());
            }
            return w.toString();
        }
    }

    private static Filter filter(Long userId, String action, String type, String search, String start, String end) {
        LocalDate from = date("start_date", start);
        LocalDate to = date("end_date", end);
        if (from != null && to != null && to.isBefore(from)) throw ApiException.validation("end_date", "The end date is before the start date.");
        return new Filter(userId, blank(action), blank(type), blank(search), from, to);
    }

    @GetMapping
    @PreAuthorize("@perm.any('view_audit_trail')")
    public PageResponse<Entry> index(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(name = "per_page", defaultValue = "20") int perPage,
                                     @RequestParam(name = "user_id", required = false) Long userId,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(name = "auditable_type", required = false) String type,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(name = "start_date", required = false) String start,
                                     @RequestParam(name = "end_date", required = false) String end) {
        Filter f = filter(userId, action, type, search, start, end);
        int size = Math.clamp(perPage, 1, 500);
        int p = Math.max(1, page);
        List<Object> args = new ArrayList<>();
        String where = f.where(args);
        Long total = jdbc.queryForObject("select count(*) from audit_trails a" + where, Long.class, args.toArray());
        args.add((long) (p - 1) * size);
        args.add(size);
        List<Entry> rows = jdbc.query(SELECT + where + " order by a.created_at desc, a.id desc offset ? rows fetch next ? rows only", (rs, i) -> entry(rs, false), args.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PageResponse.Pagination(t, size, p, (int) Math.max(1, (t + size - 1) / size)));
    }

    public record Verification(long checked, long intact, long unsealed, List<Long> altered, boolean complete) {
    }

    /**
     * Recomputes every row's seal within the filter. Rows written before sealing began are counted
     * as unsealed; a row whose seal does not match was changed outside PAMS. The table itself only
     * accepts inserts, so a deleted row shows as a gap only to the database administrator.
     */
    @GetMapping("/verify")
    @PreAuthorize("@perm.any('view_audit_trail')")
    public Verification verify(@RequestParam(name = "user_id", required = false) Long userId,
                               @RequestParam(required = false) String action,
                               @RequestParam(name = "auditable_type", required = false) String type,
                               @RequestParam(required = false) String search,
                               @RequestParam(name = "start_date", required = false) String start,
                               @RequestParam(name = "end_date", required = false) String end) {
        Filter f = filter(userId, action, type, search, start, end);
        List<Object> args = new ArrayList<>();
        String where = f.where(args);
        long[] n = new long[3];
        List<Long> altered = new ArrayList<>();
        jdbc.query("""
                select a.id, a.user_name, a.user_role, a.action, a.auditable_type, a.auditable_id, a.description, a.old_values, a.new_values,
                       a.ip_address, a.user_agent, a.url, a.method, a.created_at, a.seal
                from audit_trails a""" + where + " order by a.id", rs -> {
            n[0]++;
            String seal = rs.getString(15);
            if (seal == null) {
                n[2]++;
                return;
            }
            var created = rs.getTimestamp(14);
            AuditService.Row row = new AuditService.Row(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, Long.class),
                    rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
                    created == null ? java.time.LocalDateTime.MIN : created.toLocalDateTime());
            if (created != null && seal.equals(audit.seal(row))) n[1]++;
            else if (altered.size() < 200) altered.add(rs.getLong(1));
        }, args.toArray());
        long alteredCount = n[0] - n[1] - n[2];
        return new Verification(n[0], n[1], n[2], altered, alteredCount == altered.size());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.any('view_audit_trail')")
    public Entry show(@PathVariable Long id) {
        List<Entry> rows = jdbc.query(SELECT + " where a.id = ?", (rs, i) -> entry(rs, true), id);
        if (rows.isEmpty()) throw ApiException.notFound("Audit entry");
        return rows.get(0);
    }

    /** Distinct values for the filter dropdowns. */
    @GetMapping("/filters")
    @PreAuthorize("@perm.any('view_audit_trail')")
    public Map<String, Object> filters() {
        return Map.of(
                "actions", jdbc.queryForList("select distinct action from audit_trails order by action", String.class),
                "types", jdbc.queryForList("select distinct auditable_type from audit_trails where auditable_type is not null", String.class)
                        .stream().map(t -> t.substring(t.lastIndexOf('\\') + 1)).distinct().sorted().toList(),
                "users", jdbc.query("""
                        select distinct a.user_id, coalesce(u.name, a.user_name) from audit_trails a left join users u on u.id = a.user_id
                        where a.user_id is not null order by 2
                        """, (rs, i) -> new Ref(rs.getLong(1), rs.getString(2))));
    }

    /** CSV of the filtered trail (newest first, at most 10,000 rows), for COA submission. The export itself is audited. */
    @GetMapping("/export")
    @PreAuthorize("@perm.any('export_audit_trail')")
    public ResponseEntity<byte[]> export(@RequestParam(name = "user_id", required = false) Long userId,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(name = "auditable_type", required = false) String type,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(name = "start_date", required = false) String start,
                                         @RequestParam(name = "end_date", required = false) String end) {
        Filter f = filter(userId, action, type, search, start, end);
        List<Object> args = new ArrayList<>();
        String where = f.where(args);
        args.add(EXPORT_LIMIT);
        List<Entry> rows = jdbc.query(SELECT + where + " order by a.created_at desc, a.id desc offset 0 rows fetch next ? rows only", (rs, i) -> entry(rs, true), args.toArray());
        StringBuilder csv = new StringBuilder("﻿Date/Time,User,Role,Action,Record,Description,IP Address,Old values,New values\r\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Entry e : rows) {
            csv.append(String.join(",",
                    cell(e.createdAt() == null ? "" : e.createdAt().format(fmt)), cell(e.userName()), cell(e.userRole()), cell(e.action()),
                    cell(e.auditableType() == null ? "" : e.auditableType() + (e.auditableId() == null ? "" : " #" + e.auditableId())),
                    cell(e.description()), cell(e.ipAddress()),
                    cell(e.oldValues() == null ? "" : e.oldValues().toString()), cell(e.newValues() == null ? "" : e.newValues().toString())))
                    .append("\r\n");
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("rows", rows.size());
        if (f.action() != null) scope.put("action", f.action());
        if (f.type() != null) scope.put("auditable_type", f.type());
        if (f.userId() != null) scope.put("user_id", f.userId());
        if (f.search() != null) scope.put("search", f.search());
        if (f.from() != null) scope.put("start_date", f.from().toString());
        if (f.to() != null) scope.put("end_date", f.to().toString());
        audit.record("exported", null, null, "Exported the audit trail (" + rows.size() + " rows, CSV)", null, scope);
        String name = "audit_trail_" + Times.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    public record EventInput(String action, String description, String auditableType, Long auditableId) {
    }

    /** Lets the client record what the server cannot see: a page printed or a file saved (TOR 6.4). */
    @PostMapping("/events")
    public Map<String, Object> event(@RequestBody EventInput in) {
        if (in.action() == null || !CLIENT_EVENTS.contains(in.action())) throw ApiException.validation("action", "Only printed or downloaded can be recorded.");
        String description = blank(in.description());
        if (description == null) throw ApiException.validation("description", "Say what was printed or downloaded.");
        if (description.length() > 500) description = description.substring(0, 500);
        String type = blank(in.auditableType());
        if (type != null && !type.matches("[A-Z][A-Za-z]{1,60}")) throw ApiException.validation("auditable_type", "Unknown record type.");
        audit.record(in.action(), type == null ? null : "App\\Models\\" + type, type == null ? null : in.auditableId(), description, null, null);
        return Map.of("message", "Recorded.");
    }

    private static final String SELECT = """
            select a.id, a.user_id, u.name, a.user_name, a.user_role, a.action, a.auditable_type, a.auditable_id, a.description,
                   a.ip_address, a.created_at, a.old_values, a.new_values, a.user_agent, a.url, a.method
            from audit_trails a left join users u on u.id = a.user_id""";

    private static Entry entry(ResultSet rs, boolean full) throws SQLException {
        Long userId = rs.getObject(2, Long.class);
        String type = rs.getString(7);
        var created = rs.getTimestamp(11);
        return new Entry(rs.getLong(1), userId == null ? null : new Ref(userId, rs.getString(3) != null ? rs.getString(3) : rs.getString(4)),
                rs.getString(4), rs.getString(5), rs.getString(6), type == null ? null : type.substring(type.lastIndexOf('\\') + 1),
                rs.getObject(8, Long.class), rs.getString(9), rs.getString(10), created == null ? null : Times.toOffset(created.toLocalDateTime()),
                full ? json(rs.getString(12)) : null, full ? json(rs.getString(13)) : null,
                full ? rs.getString(14) : null, full ? rs.getString(15) : null, full ? rs.getString(16) : null);
    }

    private static JsonNode json(String s) {
        if (s == null || s.isBlank() || s.equals("null")) return null;
        try {
            return JSON.readTree(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** CSV cell, quoted, with spreadsheet formula injection neutralised. */
    private static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
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
