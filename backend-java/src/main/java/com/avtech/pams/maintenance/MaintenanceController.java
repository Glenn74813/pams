package com.avtech.pams.maintenance;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maintenance of assets (TOR 1.4.5): preventive schedules that repeat every so many months,
 * corrective repairs, inspections and calibrations, with what each cost. Overdue and upcoming
 * work is listed for the property office.
 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    static final Set<String> TYPES = Set.of("preventive", "corrective", "inspection", "calibration");
    private static final String READ = "@perm.any('view_properties', 'edit_properties', 'view_reports')";
    private static final String MANAGE = "@perm.any('edit_properties')";
    /** "Upcoming" means scheduled within this many days. */
    static final int UPCOMING_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public MaintenanceController(JdbcTemplate jdbc, CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
    }

    public record MaintenanceView(Long id, Long propertyId, String tagNumber, String itemName, String holder, String location,
                                  String maintenanceType, String description, LocalDate scheduledDate, Integer intervalMonths,
                                  String status, boolean overdue, LocalDate completedDate, String performedBy, BigDecimal cost,
                                  String remarks, String createdBy, String completedBy) {
    }

    public record ScheduleInput(Long propertyId, String maintenanceType, String description, LocalDate scheduledDate,
                                Integer intervalMonths, String remarks) {
    }

    public record CompleteInput(LocalDate completedDate, String performedBy, BigDecimal cost, String remarks) {
    }

    @GetMapping
    @PreAuthorize(READ)
    public PageResponse<MaintenanceView> index(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String due,
                                               @RequestParam(name = "property_id", required = false) Long propertyId,
                                               @RequestParam(required = false) String search) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            if (!Set.of("scheduled", "done", "cancelled").contains(status)) throw ApiException.validation("status", "Unknown status.");
            where.append(" and m.status = ?");
            args.add(status);
        }
        LocalDate today = LocalDate.now(Times.ZONE);
        if ("overdue".equals(due)) {
            where.append(" and m.status = 'scheduled' and m.scheduled_date < ?");
            args.add(Date.valueOf(today));
        } else if ("upcoming".equals(due)) {
            where.append(" and m.status = 'scheduled' and m.scheduled_date between ? and ?");
            args.add(Date.valueOf(today));
            args.add(Date.valueOf(today.plusDays(UPCOMING_DAYS)));
        } else if (due != null && !due.isBlank()) {
            throw ApiException.validation("due", "Use overdue or upcoming.");
        }
        if (propertyId != null) {
            where.append(" and m.property_id = ?");
            args.add(propertyId);
        }
        if (search != null && !search.isBlank()) {
            where.append(" and (lower(p.tag_number) like ? or lower(i.name) like ? or lower(m.description) like ?)");
            String like = "%" + search.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String from = " from property_maintenance m join properties p on p.id = m.property_id join items i on i.id = p.item_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
        int size = 20;
        int pageNo = Math.max(1, page);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((pageNo - 1) * size);
        pageArgs.add(size);
        List<Long> ids = jdbc.queryForList("select m.id" + from + where
                + " order by case when m.status = 'scheduled' then 0 else 1 end, m.scheduled_date, m.id offset ? rows fetch next ? rows only", Long.class, pageArgs.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(views(ids), new PageResponse.Pagination(t, size, pageNo, (int) Math.max(1, (t + size - 1) / size)));
    }

    /** Counts for the dashboard and the maintenance page header. */
    @GetMapping("/summary")
    @PreAuthorize(READ)
    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overdue", count("status = 'scheduled' and scheduled_date < ?", Date.valueOf(today)));
        m.put("upcoming", count("status = 'scheduled' and scheduled_date between ? and ?", Date.valueOf(today), Date.valueOf(today.plusDays(UPCOMING_DAYS))));
        m.put("done_this_year", count("status = 'done' and completed_date >= ?", Date.valueOf(today.withDayOfYear(1))));
        BigDecimal cost = jdbc.queryForObject("select coalesce(sum(cost), 0) from property_maintenance where status = 'done' and completed_date >= ?",
                BigDecimal.class, Date.valueOf(today.withDayOfYear(1)));
        m.put("cost_this_year", cost);
        m.put("upcoming_days", UPCOMING_DAYS);
        return m;
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<MaintenanceView> schedule(@RequestBody ScheduleInput in) {
        Map<String, Object> p = property(in.propertyId());
        validate(in);
        Timestamp now = Timestamp.valueOf(Times.now());
        Long id = Inserts.insert(jdbc, """
                insert into property_maintenance (property_id, maintenance_type, description, scheduled_date, interval_months, status, remarks, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'scheduled', ?, ?, ?, ?)""", in.propertyId(), in.maintenanceType(), in.description().trim(),
                Date.valueOf(in.scheduledDate()), in.intervalMonths(), blank(in.remarks()), current.user().getId(), now, now);
        audit.record("created", "PropertyMaintenance", id, "Scheduled " + in.maintenanceType() + " maintenance of asset " + p.get("tag_number")
                + " on " + in.scheduledDate(), null, Map.of("scheduled_date", in.scheduledDate().toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(id)).get(0));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public MaintenanceView update(@PathVariable Long id, @RequestBody ScheduleInput in) {
        MaintenanceView before = open(id);
        ScheduleInput fixed = new ScheduleInput(before.propertyId(), in.maintenanceType(), in.description(), in.scheduledDate(), in.intervalMonths(), in.remarks());
        validate(fixed);
        jdbc.update("update property_maintenance set maintenance_type = ?, description = ?, scheduled_date = ?, interval_months = ?, remarks = ?, updated_at = ? where id = ?",
                fixed.maintenanceType(), fixed.description().trim(), Date.valueOf(fixed.scheduledDate()), fixed.intervalMonths(), blank(fixed.remarks()),
                Timestamp.valueOf(Times.now()), id);
        audit.record("updated", "PropertyMaintenance", id, "Rescheduled maintenance of asset " + before.tagNumber(),
                Map.of("scheduled_date", before.scheduledDate().toString()), Map.of("scheduled_date", fixed.scheduledDate().toString()));
        return views(List.of(id)).get(0);
    }

    /** Records the work as done; a repeating schedule gets its next date counted from the day it was done. */
    @PostMapping("/{id}/complete")
    @PreAuthorize(MANAGE)
    @Transactional
    public Map<String, Object> complete(@PathVariable Long id, @RequestBody CompleteInput in) {
        MaintenanceView m = open(id);
        LocalDate done = in.completedDate() == null ? LocalDate.now(Times.ZONE) : in.completedDate();
        if (done.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("completed_date", "The date done cannot be in the future.");
        if (in.cost() != null && in.cost().signum() < 0) throw ApiException.validation("cost", "The cost cannot be negative.");
        String by = blank(in.performedBy());
        if (by != null && by.length() > 255) throw ApiException.validation("performed_by", "Keep this within 255 characters.");
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("update property_maintenance set status = 'done', completed_date = ?, performed_by = ?, cost = ?, remarks = coalesce(?, remarks), completed_by = ?, updated_at = ? where id = ?",
                Date.valueOf(done), by, in.cost(), blank(in.remarks()), current.user().getId(), now, id);
        Long next = null;
        if (m.intervalMonths() != null) {
            LocalDate nextDate = done.plusMonths(m.intervalMonths());
            next = Inserts.insert(jdbc, """
                    insert into property_maintenance (property_id, maintenance_type, description, scheduled_date, interval_months, status, created_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, 'scheduled', ?, ?, ?)""", m.propertyId(), m.maintenanceType(), m.description(), Date.valueOf(nextDate),
                    m.intervalMonths(), current.user().getId(), now, now);
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("completed_date", done.toString());
        after.put("cost", in.cost());
        audit.record("updated", "PropertyMaintenance", id, "Completed maintenance of asset " + m.tagNumber(), null, after);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", next == null ? "Maintenance recorded as done." : "Maintenance recorded; the next one is scheduled.");
        res.put("maintenance", views(List.of(id)).get(0));
        res.put("next", next == null ? null : views(List.of(next)).get(0));
        return res;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE)
    @Transactional
    public MaintenanceView cancel(@PathVariable Long id) {
        MaintenanceView m = open(id);
        jdbc.update("update property_maintenance set status = 'cancelled', updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        audit.record("updated", "PropertyMaintenance", id, "Cancelled maintenance of asset " + m.tagNumber(), null, null);
        return views(List.of(id)).get(0);
    }

    // ───────────────────────── helpers ─────────────────────────

    private void validate(ScheduleInput in) {
        if (in.maintenanceType() == null || !TYPES.contains(in.maintenanceType())) throw ApiException.validation("maintenance_type", "Choose preventive, corrective, inspection or calibration.");
        if (in.description() == null || in.description().isBlank()) throw ApiException.validation("description", "Describe the work.");
        if (in.description().trim().length() > 500) throw ApiException.validation("description", "Keep the description within 500 characters.");
        if (in.scheduledDate() == null) throw ApiException.validation("scheduled_date", "Give the date it is due.");
        if (in.intervalMonths() != null && (in.intervalMonths() < 1 || in.intervalMonths() > 120)) throw ApiException.validation("interval_months", "Repeat every 1 to 120 months.");
    }

    private Map<String, Object> property(Long id) {
        if (id == null) throw ApiException.validation("property_id", "Choose the asset.");
        List<Map<String, Object>> rows = jdbc.queryForList("select id, tag_number, status from properties where id = ?", id);
        if (rows.isEmpty()) throw ApiException.validation("property_id", "Choose the asset.");
        if ("disposed".equals(rows.get(0).get("status"))) throw ApiException.validation("property_id", "Disposed assets are not maintained.");
        return rows.get(0);
    }

    private MaintenanceView open(Long id) {
        List<MaintenanceView> v = views(List.of(id));
        if (v.isEmpty()) throw ApiException.notFound("Maintenance");
        if (!"scheduled".equals(v.get(0).status())) throw ApiException.of(HttpStatus.CONFLICT, "Only scheduled maintenance can be changed.");
        return v.get(0);
    }

    private long count(String where, Object... args) {
        Long n = jdbc.queryForObject("select count(*) from property_maintenance where " + where, Long.class, args);
        return n == null ? 0 : n;
    }

    private List<MaintenanceView> views(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<Long, MaintenanceView> byId = new LinkedHashMap<>();
        jdbc.query("""
                select m.*, p.tag_number, i.name as item_name, h.name as holder, l.name as location, c.name as created_name, d.name as completed_name
                from property_maintenance m
                join properties p on p.id = m.property_id
                join items i on i.id = p.item_id
                left join users h on h.id = p.assigned_to
                left join asset_locations l on l.id = p.location_id
                left join users c on c.id = m.created_by
                left join users d on d.id = m.completed_by
                where m.id in (""" + in + ")", rs -> {
            LocalDate scheduled = rs.getDate("scheduled_date").toLocalDate();
            String status = rs.getString("status");
            Date completed = rs.getDate("completed_date");
            int interval = rs.getInt("interval_months");
            boolean noInterval = rs.wasNull();
            byId.put(rs.getLong("id"), new MaintenanceView(rs.getLong("id"), rs.getLong("property_id"), rs.getString("tag_number"), rs.getString("item_name"),
                    rs.getString("holder"), rs.getString("location"), rs.getString("maintenance_type"), rs.getString("description"), scheduled,
                    noInterval ? null : interval, status, "scheduled".equals(status) && scheduled.isBefore(today),
                    completed == null ? null : completed.toLocalDate(), rs.getString("performed_by"), rs.getBigDecimal("cost"),
                    rs.getString("remarks"), rs.getString("created_name"), rs.getString("completed_name")));
        }, ids.toArray());
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
