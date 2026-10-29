package com.avtech.pams.monitoring;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory monitoring and controls: stock levels against min/max, a re-order report with a
 * suggested quantity from recent consumption (TOR C 2.5, 9.3), fast/slow/non-moving items (2.4),
 * request trends, and the procurement watch list.
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    /** Consumption is averaged over this many days to estimate monthly usage. */
    static final int USAGE_DAYS = 90;

    private final JdbcTemplate jdbc;
    private final ProcurementWatch watch;
    private final AlertJobs alerts;
    private final Db db;

    public MonitoringController(JdbcTemplate jdbc, ProcurementWatch watch, AlertJobs alerts, Db db) {
        this.jdbc = jdbc;
        this.db = db;
        this.watch = watch;
        this.alerts = alerts;
    }

    public record Category(Long id, String name) {
    }

    /**
     * One item's position. {@code level}: out | low | normal | over | untracked (no levels set).
     * {@code monthlyUsage} is issues over the last 90 days per 30 days; {@code suggestedOrder} tops the
     * item up to its maximum (or to twice its minimum when no maximum is set).
     */
    public record StockRow(Long id, String code, String name, Category category, String unitOfMeasure, int currentStock, int minStock,
                           int maxStock, String level, int deficit, BigDecimal monthlyUsage, BigDecimal monthsOfCover, int suggestedOrder,
                           BigDecimal unitCost, BigDecimal suggestedValue, int issuedQuantity, String movement) {
    }

    @GetMapping("/stock")
    @PreAuthorize("@perm.any('view_monitoring', 'view_warehouse', 'manage_warehouse')")
    public Map<String, Object> stock(@RequestParam(name = "category_id", required = false) Long categoryId) {
        LocalDate since = LocalDate.now(Times.ZONE).minusDays(USAGE_DAYS);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(since));
        String where = "";
        if (categoryId != null) {
            where = " where i.category_id = ?";
            args.add(categoryId);
        }
        List<StockRow> rows = jdbc.query("""
                select i.id, i.code, i.name, c.id cid, c.name cname, i.unit_of_measure, i.current_stock, i.min_stock, i.max_stock, i.is_expandable,
                       coalesce(i.weighted_average_cost, 0) cost,
                       coalesce((select sum(abs(l.quantity)) from inventory_logs l where l.item_id = i.id and l.action = 'item_issued'
                                 and l.created_at >= ?), 0) issued
                from items i left join categories c on c.id = i.category_id""" + where + " order by i.name", (rs, n) -> {
            int cur = rs.getInt("current_stock"), min = rs.getInt("min_stock"), max = rs.getInt("max_stock"), issued = rs.getInt("issued");
            // PPE is tracked per asset, not as stock to re-order.
            if (rs.getBoolean("is_expandable")) return null;
            String level = min == 0 && max == 0 ? "untracked" : cur <= 0 ? "out" : min > 0 && cur <= min ? "low" : max > 0 && cur > max ? "over" : "normal";
            BigDecimal monthly = BigDecimal.valueOf(issued * 30L).divide(BigDecimal.valueOf(USAGE_DAYS), 2, RoundingMode.HALF_UP);
            BigDecimal cover = monthly.signum() == 0 ? null : BigDecimal.valueOf(Math.max(cur, 0)).divide(monthly, 1, RoundingMode.HALF_UP);
            int target = max > 0 ? max : min * 2;
            int suggested = min > 0 && ("out".equals(level) || "low".equals(level)) ? Math.max(target - cur, min - cur + 1) : 0;
            BigDecimal cost = rs.getBigDecimal("cost");
            Long cid = (Long) rs.getObject("cid");
            return new StockRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"), cid == null ? null : new Category(cid, rs.getString("cname")),
                    rs.getString("unit_of_measure"), cur, min, max, level, min > 0 ? Math.max(min - cur, 0) : 0, monthly, cover,
                    Math.max(suggested, 0), cost, cost.multiply(BigDecimal.valueOf(Math.max(suggested, 0))).setScale(2, RoundingMode.HALF_UP), issued, null);
        }, args.toArray()).stream().filter(java.util.Objects::nonNull).toList();

        // Movement class over the usage window: the items making up the top 80% of units issued are
        // fast-moving, the rest that moved are slow-moving, and stocked items that did not move are non-moving.
        long totalIssued = rows.stream().mapToLong(StockRow::issuedQuantity).sum();
        List<StockRow> byIssued = new ArrayList<>(rows);
        byIssued.sort((a, b) -> Integer.compare(b.issuedQuantity(), a.issuedQuantity()));
        Map<Long, String> movement = new LinkedHashMap<>();
        long running = 0;
        for (StockRow r : byIssued) {
            if (r.issuedQuantity() == 0) {
                movement.put(r.id(), r.currentStock() > 0 ? "non_moving" : "idle");
                continue;
            }
            movement.put(r.id(), running < totalIssued * 0.8 ? "fast" : "slow");
            running += r.issuedQuantity();
        }
        rows = rows.stream().map(r -> new StockRow(r.id(), r.code(), r.name(), r.category(), r.unitOfMeasure(), r.currentStock(), r.minStock(),
                r.maxStock(), r.level(), r.deficit(), r.monthlyUsage(), r.monthsOfCover(), r.suggestedOrder(), r.unitCost(), r.suggestedValue(),
                r.issuedQuantity(), movement.get(r.id()))).toList();

        Map<String, Long> levels = new LinkedHashMap<>();
        for (String l : List.of("out", "low", "normal", "over", "untracked")) levels.put(l, rows.stream().filter(r -> r.level().equals(l)).count());
        Map<String, Long> moves = new LinkedHashMap<>();
        for (String m : List.of("fast", "slow", "non_moving", "idle")) moves.put(m, rows.stream().filter(r -> m.equals(r.movement())).count());
        List<StockRow> reorder = rows.stream().filter(r -> r.minStock() > 0 && (r.level().equals("out") || r.level().equals("low")))
                .sorted((a, b) -> a.level().equals(b.level()) ? Integer.compare(b.deficit(), a.deficit()) : a.level().equals("out") ? -1 : 1).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_items", rows.size());
        summary.put("out_of_stock", levels.get("out"));
        summary.put("low_stock", levels.get("low"));
        summary.put("pending_requests", jdbc.queryForObject("select count(*) from requests where status in ('pending', 'approved', 'partially_fulfilled')", Long.class));
        summary.put("inventory_value", rows.stream().map(r -> r.unitCost().multiply(BigDecimal.valueOf(Math.max(r.currentStock(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        summary.put("reorder_value", reorder.stream().map(StockRow::suggestedValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        LocalDate today = LocalDate.now(Times.ZONE);
        summary.put("expired_lots", jdbc.queryForObject("select count(*) from stocks where quantity > 0 and expiration_date < ?", Long.class, Date.valueOf(today)));
        summary.put("expiring_lots", jdbc.queryForObject("select count(*) from stocks where quantity > 0 and expiration_date between ? and ?", Long.class,
                Date.valueOf(today), Date.valueOf(today.plusDays(30))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("usage_days", USAGE_DAYS);
        out.put("summary", summary);
        out.put("levels", levels);
        out.put("movement", moves);
        out.put("items", rows);
        out.put("reorder", reorder);
        out.put("categories", jdbc.query("select id, name from categories order by name", (rs, n) -> new Category(rs.getLong(1), rs.getString(2))));
        return out;
    }

    public record Trend(int year, int month, String label, long totalRequests, long fulfilled, long rejected, long open, long unitsIssued) {
    }

    @GetMapping("/trends")
    @PreAuthorize("@perm.any('view_monitoring', 'view_warehouse', 'manage_warehouse', 'view_requests')")
    public List<Trend> trends(@RequestParam(defaultValue = "6") int months) {
        int n = Math.max(1, Math.min(months, 24));
        YearMonth last = YearMonth.now(Times.ZONE);
        YearMonth first = last.minusMonths(n - 1L);
        Map<YearMonth, long[]> counts = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) counts.put(m, new long[5]);
        String y = db.year("request_date"), mo = db.month("request_date");
        jdbc.query("select " + y + ", " + mo + ", count(*),"
                + " sum(case when status = 'fulfilled' then 1 else 0 end), sum(case when status = 'rejected' then 1 else 0 end)"
                + " from requests where request_date >= ? group by " + y + ", " + mo, rs -> {
            long[] c = counts.get(YearMonth.of(rs.getInt(1), rs.getInt(2)));
            if (c == null) return;
            c[0] = rs.getLong(3);
            c[1] = rs.getLong(4);
            c[2] = rs.getLong(5);
        }, Date.valueOf(first.atDay(1)));
        String ly = db.year("created_at"), lm = db.month("created_at");
        jdbc.query("select " + ly + ", " + lm + ", sum(abs(quantity))"
                + " from inventory_logs where action = 'item_issued' and created_at >= ? group by " + ly + ", " + lm, rs -> {
            long[] c = counts.get(YearMonth.of(rs.getInt(1), rs.getInt(2)));
            if (c != null) c[4] = rs.getLong(3);
        }, java.sql.Timestamp.valueOf(first.atDay(1).atStartOfDay()));
        List<Trend> out = new ArrayList<>();
        counts.forEach((m, c) -> out.add(new Trend(m.getYear(), m.getMonthValue(),
                m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + m.getYear(),
                c[0], c[1], c[2], c[0] - c[1] - c[2], c[4])));
        return out;
    }

    @GetMapping("/procurement")
    @PreAuthorize("@perm.any('view_monitoring', 'view_procurement', 'view_bidding', 'view_rfq', 'view_contracts', 'manage_bidding')")
    public Map<String, Object> procurement() {
        List<ProcurementWatch.Entry> rows = watch.scan();
        Map<String, Long> byKind = new LinkedHashMap<>();
        rows.forEach(r -> byKind.merge(r.kind(), 1L, Long::sum));
        return Map.of("overdue", rows.stream().filter(r -> ProcurementWatch.OVERDUE.equals(r.severity())).count(),
                "due_soon", rows.stream().filter(r -> ProcurementWatch.DUE_SOON.equals(r.severity())).count(),
                "by_kind", byKind, "rows", rows);
    }

    public record RunInput(String job) {
    }

    /** Runs one of the daily alert jobs now (or all of them), instead of waiting for its schedule. */
    @PostMapping("/alerts/run")
    @PreAuthorize("@perm.any('manage_system_settings')")
    public Map<String, Object> runAlerts(@RequestBody(required = false) RunInput in) {
        String job = in == null || in.job() == null || in.job().isBlank() ? "all" : in.job().trim();
        if (!job.equals("all") && !AlertJobs.JOBS.contains(job)) throw ApiException.validation("job", "Unknown job. Use one of: all, " + String.join(", ", AlertJobs.JOBS) + ".");
        Map<String, String> results = new LinkedHashMap<>();
        for (String j : job.equals("all") ? AlertJobs.JOBS : List.of(job)) results.put(j, alerts.run(j));
        return Map.of("results", results);
    }

    @GetMapping("/alerts")
    @PreAuthorize("@perm.any('manage_system_settings')")
    public Map<String, Object> alertJobs() {
        return Map.of("jobs", AlertJobs.JOBS, "schedule", AlertJobs.SCHEDULE);
    }
}
