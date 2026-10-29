package com.avtech.pams.report;

import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Reports page: the inventory and request-log reports, the RSMI summary, and the on-screen
 * previews of the COA forms (RPCI, RPCSP, RPCPPE, Property Card, WMR, IIRUP, RLSDDP) plus the
 * reconciliation and aging reports — the same data the Laravel ReportController returned.
 * The Excel forms themselves are produced by the report.coa forms.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("@perm.any('view_reports', 'generate_reports')")
public class ReportController {

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final ObjectMapper json;
    private final Db db;

    public ReportController(JdbcTemplate jdbc, CurrentUser current, ObjectMapper json, Db db) {
        this.jdbc = jdbc;
        this.db = db;
        this.current = current;
        this.json = json;
    }

    /** Filter choices for the report forms. */
    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of(
                "categories", jdbc.queryForList("select id, name from categories order by name"),
                "departments", jdbc.queryForList("select id, name from departments order by name"));
    }

    // ───────────────────────── RSMI summary ─────────────────────────

    public record RsmiInput(Integer year, Integer month) {
    }

    /** Generates and stores an RSMI summary (rsmi_reports), numbered RSMI-YYYY-MM-NNN as before. */
    @PostMapping({"/rsmi/generate", "/rsmi"})
    @Transactional
    public Map<String, Object> generateRsmi(@RequestBody RsmiInput in) {
        if (in.year() == null || in.month() == null || in.month() < 1 || in.month() > 12) throw ApiException.validation("month", "Choose the year and month.");
        Map<String, Object> data = rsmiData(in.year(), in.month());
        return store(in.year(), in.month(), data, current.user().getId());
    }

    /** Also used by the month-end job. */
    public Map<String, Object> store(int year, int month, Map<String, Object> data, Long by) {
        String last = jdbc.query("select report_number from rsmi_reports where year = ? and month = ? order by id desc offset 0 rows fetch next 1 rows only",
                rs -> rs.next() ? rs.getString(1) : null, year, month);
        int seq = last == null ? 1 : Integer.parseInt(last.substring(last.length() - 3)) + 1;
        String number = String.format("RSMI-%04d-%02d-%03d", year, month, seq);
        Timestamp now = Timestamp.valueOf(Times.now());
        String payload = json.writeValueAsString(data);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("""
                    insert into rsmi_reports (report_number, year, month, data, generated_at, generated_by, created_at, updated_at)
                    values (?, ?, ?, """ + db.jsonParam() + ", ?, ?, ?, ?)", new String[]{"id"});
            ps.setString(1, number);
            ps.setInt(2, year);
            ps.setInt(3, month);
            ps.setString(4, payload);
            ps.setTimestamp(5, now);
            ps.setObject(6, by, java.sql.Types.BIGINT);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            return ps;
        }, key);
        Long id = key.getKeyAs(Number.class).longValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("report_number", number);
        out.put("year", year);
        out.put("month", month);
        out.put("generated_at", Times.toOffset(now.toLocalDateTime()));
        out.put("generated_by", by);
        out.putAll(data);
        return out;
    }

    public Map<String, Object> rsmiData(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Date start = Date.valueOf(ym.atDay(1)), end = Date.valueOf(ym.atEndOfMonth());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("total_requests", n("select count(*) from requests where request_date between ? and ?", start, end));
        d.put("fulfilled_requests", n("select count(*) from requests where request_date between ? and ? and status = 'fulfilled'", start, end));
        d.put("pending_requests", n("select count(*) from requests where request_date between ? and ? and status in ('pending', 'partially_fulfilled')", start, end));
        d.put("rejected_requests", n("select count(*) from requests where request_date between ? and ? and status = 'rejected'", start, end));
        Map<String, Object> byDept = new LinkedHashMap<>();
        jdbc.query("""
                select coalesce(d.name, ''), count(*), sum(case when r.status = 'fulfilled' then 1 else 0 end),
                       sum(case when r.status in ('pending', 'partially_fulfilled') then 1 else 0 end)
                from requests r left join departments d on d.id = r.department_id where r.request_date between ? and ? group by d.name order by d.name""",
                rs -> { byDept.put(rs.getString(1), Map.of("total", rs.getLong(2), "fulfilled", rs.getLong(3), "pending", rs.getLong(4))); }, start, end);
        d.put("requests_by_department", byDept);
        Map<String, Object> items = new LinkedHashMap<>();
        jdbc.query("""
                select coalesce(i.name, ''), sum(ri.quantity), sum(coalesce(ri.approved_quantity, 0)), count(*)
                from request_items ri join requests r on r.id = ri.request_id left join items i on i.id = ri.item_id
                where r.request_date between ? and ? group by i.name order by i.name""",
                rs -> { items.put(rs.getString(1), Map.of("total_quantity", rs.getLong(2), "total_approved", rs.getLong(3), "request_count", rs.getLong(4))); }, start, end);
        d.put("items_requested", items);
        Map<String, Object> trend = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth t = YearMonth.of(year, m);
            Date a = Date.valueOf(t.atDay(1)), b = Date.valueOf(t.atEndOfMonth());
            trend.put(String.valueOf(m), Map.of("month", m, "total", n("select count(*) from requests where request_date between ? and ?", a, b),
                    "fulfilled", n("select count(*) from requests where request_date between ? and ? and status = 'fulfilled'", a, b)));
        }
        d.put("monthly_trend", trend);
        d.put("year", year);
        d.put("month", month);
        return d;
    }

    @GetMapping("/rsmi/history")
    public List<Map<String, Object>> rsmiHistory() {
        return jdbc.query("""
                select r.id, r.report_number, r.year, r.month, r.generated_at, u.name from rsmi_reports r left join users u on u.id = r.generated_by
                order by r.year desc, r.month desc, r.id desc offset 0 rows fetch next 36 rows only""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong(1));
            m.put("report_number", rs.getString(2));
            m.put("year", rs.getInt(3));
            m.put("month", rs.getInt(4));
            m.put("generated_at", Times.toOffset(rs.getTimestamp(5).toLocalDateTime()));
            m.put("generated_by", rs.getString(6) == null ? "System (month-end)" : rs.getString(6));
            return m;
        });
    }

    // ───────────────────────── inventory & request logs ─────────────────────────

    @GetMapping("/inventory")
    public Map<String, Object> inventory(@RequestParam(name = "category_id", required = false) Long categoryId,
                                         @RequestParam(required = false) String status) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (categoryId != null) {
            w.append(" and i.category_id = ?");
            args.add(categoryId);
        }
        if ("low_stock".equals(status)) w.append(" and i.current_stock < i.min_stock");
        else if ("out_of_stock".equals(status)) w.append(" and i.current_stock = 0");
        else if ("in_stock".equals(status)) w.append(" and i.current_stock > 0");
        else if (status != null && !status.isBlank()) throw ApiException.validation("status", "Unknown status.");
        List<Map<String, Object>> items = jdbc.query("""
                select i.id, i.code, i.name, i.description, i.unit_of_measure, i.current_stock, i.min_stock, i.max_stock,
                       coalesce(i.weighted_average_cost, 0) cost, c.id cid, c.name cname
                from items i left join categories c on c.id = i.category_id""" + w + " order by i.name", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("code", rs.getString("code"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            m.put("unit_of_measure", rs.getString("unit_of_measure"));
            m.put("current_stock", rs.getInt("current_stock"));
            m.put("min_stock", rs.getInt("min_stock"));
            m.put("max_stock", rs.getInt("max_stock"));
            m.put("weighted_average_cost", rs.getBigDecimal("cost"));
            m.put("total_value", rs.getBigDecimal("cost").multiply(BigDecimal.valueOf(rs.getInt("current_stock"))).setScale(2, RoundingMode.HALF_UP));
            m.put("category", rs.getObject("cid") == null ? null : Map.of("id", rs.getLong("cid"), "name", rs.getString("cname")));
            return m;
        }, args.toArray());
        Map<String, Object> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> i : items) {
            @SuppressWarnings("unchecked")
            String c = i.get("category") == null ? "" : (String) ((Map<String, Object>) i.get("category")).get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> agg = (Map<String, Object>) byCategory.computeIfAbsent(c, k -> new LinkedHashMap<>(Map.of("count", 0L, "value", BigDecimal.ZERO)));
            agg.put("count", (Long) agg.get("count") + 1);
            agg.put("value", ((BigDecimal) agg.get("value")).add((BigDecimal) i.get("total_value")));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_items", items.size());
        summary.put("total_value", items.stream().map(i -> (BigDecimal) i.get("total_value")).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("low_stock_items", items.stream().filter(i -> (int) i.get("current_stock") > 0 && (int) i.get("current_stock") < (int) i.get("min_stock")).count());
        summary.put("out_of_stock_items", items.stream().filter(i -> (int) i.get("current_stock") == 0).count());
        summary.put("by_category", byCategory);
        return Map.of("items", items, "summary", summary, "generated_at", Times.toOffset(Times.now()));
    }

    @GetMapping("/request-logs")
    public Map<String, Object> requestLogs(@RequestParam(name = "start_date", required = false) LocalDate start,
                                           @RequestParam(name = "end_date", required = false) LocalDate end,
                                           @RequestParam(name = "department_id", required = false) Long departmentId) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (start != null) {
            w.append(" and l.requested_at >= ?");
            args.add(Timestamp.valueOf(start.atStartOfDay()));
        }
        if (end != null) {
            w.append(" and l.requested_at < ?");
            args.add(Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        }
        if (departmentId != null) {
            w.append(" and l.department_id = ?");
            args.add(departmentId);
        }
        List<Map<String, Object>> logs = jdbc.query("""
                select l.id, l.quantity, l.requested_at, d.id did, d.name dname, i.id iid, i.code, i.name iname, u.id uid, u.name uname
                from request_logs l left join departments d on d.id = l.department_id left join items i on i.id = l.item_id
                left join users u on u.id = l.requested_by""" + w + " order by l.requested_at desc offset 0 rows fetch next 5000 rows only", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("quantity", rs.getInt("quantity"));
            m.put("requested_at", rs.getTimestamp("requested_at") == null ? null : Times.toOffset(rs.getTimestamp("requested_at").toLocalDateTime()));
            m.put("department", rs.getObject("did") == null ? null : Map.of("id", rs.getLong("did"), "name", rs.getString("dname")));
            m.put("item", rs.getObject("iid") == null ? null : Map.of("id", rs.getLong("iid"), "code", rs.getString("code"), "name", rs.getString("iname")));
            m.put("requester", rs.getObject("uid") == null ? null : Map.of("id", rs.getLong("uid"), "name", rs.getString("uname")));
            return m;
        }, args.toArray());
        Map<String, Map<String, Long>> byDept = new LinkedHashMap<>(), byItem = new LinkedHashMap<>();
        for (Map<String, Object> l : logs) {
            @SuppressWarnings("unchecked")
            String dn = l.get("department") == null ? "" : (String) ((Map<String, Object>) l.get("department")).get("name");
            @SuppressWarnings("unchecked")
            String in = l.get("item") == null ? "" : (String) ((Map<String, Object>) l.get("item")).get("name");
            long q = (int) l.get("quantity");
            byDept.computeIfAbsent(dn, k -> new LinkedHashMap<>(Map.of("count", 0L, "quantity", 0L))).merge("count", 1L, Long::sum);
            byDept.get(dn).merge("quantity", q, Long::sum);
            byItem.computeIfAbsent(in, k -> new LinkedHashMap<>(Map.of("count", 0L, "quantity", 0L))).merge("count", 1L, Long::sum);
            byItem.get(in).merge("quantity", q, Long::sum);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_requests", logs.size());
        summary.put("total_quantity", logs.stream().mapToLong(l -> (int) l.get("quantity")).sum());
        summary.put("by_department", byDept);
        summary.put("by_item", byItem);
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", logs.isEmpty() ? null : logs.get(logs.size() - 1).get("requested_at"));
        period.put("end", logs.isEmpty() ? null : logs.get(0).get("requested_at"));
        summary.put("time_period", period);
        return Map.of("logs", logs, "summary", summary, "generated_at", Times.toOffset(Times.now()));
    }

    // ───────────────────────── physical count previews ─────────────────────────

    @GetMapping("/rpci/preview")
    public Map<String, Object> previewRpci(@RequestParam(name = "inventory_type", required = false) String type) {
        String where = "consumable".equals(type) ? "i.is_expandable = " + db.f() + " and i.is_semi_expandable = " + db.f()
                : "semi_expandable".equals(type) ? "i.is_semi_expandable = " + db.t() : "i.is_expandable = " + db.f();
        List<Map<String, Object>> rows = jdbc.query("""
                select c.name, i.name, i.code, i.unit_of_measure, coalesce(i.weighted_average_cost, 0), i.current_stock
                from items i left join categories c on c.id = i.category_id""" + " where " + where + " order by i.name", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("article", rs.getString(1));
            m.put("description", rs.getString(2));
            m.put("stock_no", rs.getString(3));
            m.put("unit", rs.getString(4));
            m.put("unit_value", rs.getBigDecimal(5));
            m.put("balance_per_card", rs.getInt(6));
            return m;
        });
        return Map.of("title", "Report on the Physical Count of Inventories (Appendix 66)", "rows", rows);
    }

    @GetMapping("/rpcsp/preview")
    public Map<String, Object> previewRpcsp() {
        List<Map<String, Object>> rows = jdbc.query("""
                select c.name, i.name, i.code, i.unit_of_measure, coalesce(i.weighted_average_cost, 0), i.current_stock
                from items i left join categories c on c.id = i.category_id where i.is_semi_expandable = """ + db.t() + " order by i.name", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("article", rs.getString(1));
            m.put("description", rs.getString(2));
            m.put("sep_no", rs.getString(3));
            m.put("unit", rs.getString(4));
            m.put("unit_value", rs.getBigDecimal(5));
            m.put("balance_per_card", rs.getInt(6));
            return m;
        });
        return Map.of("title", "Report on the Physical Count of Semi-Expendable Property (Appendix 68)", "rows", rows);
    }

    @GetMapping("/rpcppe/preview")
    public Map<String, Object> previewRpcppe(@RequestParam(name = "ppe_type", required = false) String type) {
        String flag = ("semi_expandable".equals(type) ? "i.is_semi_expandable" : "i.is_expandable") + " = " + db.t();
        List<Map<String, Object>> rows = jdbc.query("""
                select c.name, i.name, p.tag_number, coalesce(p.acquisition_cost, 0), p.condition, u.name
                from properties p join items i on i.id = p.item_id left join categories c on c.id = i.category_id left join users u on u.id = p.assigned_to""" + " where " + flag + " order by p.tag_number", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("article", rs.getString(1));
            m.put("description", rs.getString(2));
            m.put("property_no", rs.getString(3));
            m.put("unit_value", rs.getBigDecimal(4));
            m.put("condition", rs.getString(5));
            m.put("accountable", rs.getString(6));
            return m;
        });
        return Map.of("title", "Report on the Physical Count of PPE (Appendix 73)", "rows", rows);
    }

    // ───────────────────────── property card ─────────────────────────

    /** Property Card (COA Appendix 69) — acquisition, transfers and disposal/status of one asset. */
    @GetMapping("/property-card/{propertyId}/preview")
    public Map<String, Object> previewPropertyCard(@PathVariable Long propertyId) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select p.*, i.name item_name, c.name category_name, u.name custodian from properties p left join items i on i.id = p.item_id
                left join categories c on c.id = i.category_id left join users u on u.id = p.assigned_to where p.id = ?""", propertyId);
        if (found.isEmpty()) throw ApiException.notFound("Property");
        Map<String, Object> p = found.get(0);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal cost = (BigDecimal) p.get("acquisition_cost");
        if (p.get("acquisition_date") != null || (cost != null && cost.signum() != 0)) {
            rows.add(card(p.get("acquisition_date"), "Acquisition", "Acquired" + (cost != null && cost.signum() != 0 ? " @ ₱" + money(cost) : ""), (String) p.get("custodian")));
        }
        jdbc.query("""
                select t.transfer_date, t.reference_number, t.transfer_reason, f.name, tu.name from property_transfers t
                left join users f on f.id = t.from_user left join users tu on tu.id = t.to_user where t.property_id = ? order by t.transfer_date, t.id""", rs -> {
            String reason = rs.getString(3);
            rows.add(card(rs.getDate(1), rs.getString(2) == null || rs.getString(2).isBlank() ? "Transfer" : rs.getString(2),
                    "Transferred from " + (rs.getString(4) == null ? "—" : rs.getString(4)) + " to " + (rs.getString(5) == null ? "—" : rs.getString(5))
                            + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"), rs.getString(5)));
        }, propertyId);
        String status = (String) p.get("status");
        if ("disposed".equals(status) || "retired".equals(status)) {
            rows.add(card(null, Character.toUpperCase(status.charAt(0)) + status.substring(1), "Asset " + status, null));
        }
        BigDecimal book = (BigDecimal) p.get("current_book_value");
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("tag_number", p.get("tag_number"));
        prop.put("serial_number", p.get("serial_number"));
        prop.put("description", p.get("item_name"));
        prop.put("article", p.get("category_name"));
        prop.put("acquisition_cost", cost == null ? BigDecimal.ZERO : cost);
        prop.put("acquisition_date", p.get("acquisition_date") == null ? null : p.get("acquisition_date").toString());
        prop.put("useful_life_years", p.get("useful_life_years"));
        prop.put("accumulated_depreciation", p.get("accumulated_depreciation") == null ? BigDecimal.ZERO : p.get("accumulated_depreciation"));
        prop.put("book_value", book != null && book.signum() != 0 ? book : cost == null ? BigDecimal.ZERO : cost);
        prop.put("condition", p.get("condition"));
        prop.put("status", status);
        prop.put("custodian", p.get("custodian"));
        return Map.of("property", prop, "rows", rows);
    }

    private static Map<String, Object> card(Object date, String ref, String particulars, String office) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date == null ? null : date.toString());
        m.put("reference", ref);
        m.put("particulars", particulars);
        m.put("office", office);
        return m;
    }

    // ───────────────────────── reconciliation & aging ─────────────────────────

    /** Items whose Stock Card balance (receipts − issues logged) differs from the recorded on-hand quantity. */
    @GetMapping("/reconciliation/preview")
    public Map<String, Object> previewReconciliation() {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("""
                select i.code, i.name, i.current_stock, coalesce(i.weighted_average_cost, 0),
                       coalesce(sum(case when l.action in ('stock_in', 'delivery_accepted') then l.quantity else 0 end), 0)
                     - coalesce(sum(case when l.action in ('stock_out', 'item_issued') then l.quantity else 0 end), 0)
                from items i left join inventory_logs l on l.item_id = i.id
                where i.is_expandable = """ + db.f() + " group by i.id, i.code, i.name, i.current_stock, i.weighted_average_cost order by i.name", rs -> {
            long card = rs.getLong(5);
            int onHand = rs.getInt(3);
            long variance = onHand - card;
            if (variance == 0) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stock_no", rs.getString(1));
            m.put("description", rs.getString(2));
            m.put("card_balance", card);
            m.put("on_hand", onHand);
            m.put("variance", variance);
            m.put("value_variance", rs.getBigDecimal(4).multiply(BigDecimal.valueOf(variance)).setScale(2, RoundingMode.HALF_UP));
            rows.add(m);
        });
        return Map.of("title", "Inventory Reconciliation — Card Balance vs. On-Hand", "count", rows.size(), "rows", rows);
    }

    @GetMapping("/inventory-aging/preview")
    public Map<String, Object> previewAging(@RequestParam(defaultValue = "180") int days) {
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (String b : List.of("0-90", "91-180", "181-365", "365+", "never")) buckets.put(b, 0L);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal[] atRisk = {BigDecimal.ZERO};
        jdbc.query("""
                select i.code, i.name, c.name, i.current_stock, coalesce(i.weighted_average_cost, 0),
                       (select max(l.created_at) from inventory_logs l where l.item_id = i.id and l.action in ('stock_out', 'item_issued'))
                from items i left join categories c on c.id = i.category_id where i.current_stock > 0 order by i.name""", rs -> {
            Timestamp last = rs.getTimestamp(6);
            Integer idle = last == null ? null : (int) ChronoUnit.DAYS.between(last.toLocalDateTime().toLocalDate(), today);
            String bucket = idle == null ? "never" : idle <= 90 ? "0-90" : idle <= 180 ? "91-180" : idle <= 365 ? "181-365" : "365+";
            buckets.merge(bucket, 1L, Long::sum);
            BigDecimal value = rs.getBigDecimal(5).multiply(BigDecimal.valueOf(rs.getInt(4))).setScale(2, RoundingMode.HALF_UP);
            boolean slow = idle == null || idle >= days;
            if (slow) atRisk[0] = atRisk[0].add(value);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stock_no", rs.getString(1));
            m.put("description", rs.getString(2));
            m.put("category", rs.getString(3));
            m.put("on_hand", rs.getInt(4));
            m.put("unit_value", rs.getBigDecimal(5));
            m.put("value", value);
            m.put("last_issued", last == null ? null : last.toLocalDateTime().toLocalDate().toString());
            m.put("days_idle", idle);
            m.put("bucket", bucket);
            m.put("slow_moving", slow);
            rows.add(m);
        });
        rows.sort((a, b) -> {
            boolean sa = (boolean) a.get("slow_moving"), sb = (boolean) b.get("slow_moving");
            if (sa != sb) return sa ? -1 : 1;
            int da = a.get("days_idle") == null ? Integer.MAX_VALUE : (int) a.get("days_idle");
            int db = b.get("days_idle") == null ? Integer.MAX_VALUE : (int) b.get("days_idle");
            return Integer.compare(db, da);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Inventory Aging / Slow-Moving Report");
        out.put("threshold_days", days);
        out.put("buckets", buckets);
        out.put("slow_moving_count", rows.stream().filter(r -> (boolean) r.get("slow_moving")).count());
        out.put("value_at_risk", atRisk[0]);
        out.put("count", rows.size());
        out.put("rows", rows);
        return out;
    }

    // ───────────────────────── disposal-driven previews ─────────────────────────

    /** Waste Materials Report preview (Appendix 65) from selected disposals. */
    @GetMapping("/wmr/preview")
    public Map<String, Object> previewWmr(@RequestParam(name = "disposal_ids", required = false) List<Long> ids) {
        List<Map<String, Object>> rows = disposals(ids).stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            boolean prop = d.get("property_id") != null;
            m.put("qty", prop ? 1 : d.get("quantity") == null ? 1 : ((Number) d.get("quantity")).intValue());
            m.put("unit", d.get("unit") == null ? "unit" : d.get("unit"));
            m.put("description", (d.get("item_name") == null ? "N/A" : d.get("item_name")) + (prop ? " (Tag: " + d.get("tag_number") + ")" : ""));
            m.put("disposal", ucfirst((String) d.get("disposal_type")));
            m.put("reason", d.get("reason") == null ? "" : d.get("reason"));
            return m;
        }).toList();
        return Map.of("title", "Waste Materials Report (Appendix 65)", "rows", rows);
    }

    /** IIRUP preview (Appendix 74) from selected unserviceable-property disposals. */
    @GetMapping("/iirup/preview")
    public Map<String, Object> previewIirup(@RequestParam(name = "disposal_ids", required = false) List<Long> ids) {
        List<Map<String, Object>> rows = disposals(ids).stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Object assigned = d.get("assigned_date");
            m.put("date_acquired", assigned == null ? "" : ((Date) assigned).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            m.put("property_no", d.get("tag_number") == null ? "" : d.get("tag_number"));
            m.put("description", d.get("item_name") == null ? "N/A" : d.get("item_name"));
            m.put("unit_cost", d.get("acquisition_cost") == null ? BigDecimal.ZERO : d.get("acquisition_cost"));
            m.put("condition", ucfirst((String) d.get("disposal_type")));
            String reason = (String) d.get("reason");
            m.put("remarks", reason == null ? "" : reason.length() > 40 ? reason.substring(0, 40) : reason);
            return m;
        }).toList();
        return Map.of("title", "Inventory & Inspection Report of Unserviceable Property (Appendix 74)", "rows", rows);
    }

    /** RLSDDP preview (Appendix 75) for a single lost/damaged property. */
    @GetMapping("/rlsddp/preview")
    public Map<String, Object> previewRlsddp(@RequestParam(name = "property_id", required = false) Long propertyId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String circumstances) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (propertyId != null) {
            jdbc.query("select p.tag_number, i.name, coalesce(p.acquisition_cost, 0) from properties p left join items i on i.id = p.item_id where p.id = ?", rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("property_no", rs.getString(1));
                m.put("description", rs.getString(2) == null ? "" : rs.getString(2));
                m.put("unit_cost", rs.getBigDecimal(3));
                m.put("status", ucfirst(status == null ? "" : status));
                String c = circumstances == null ? "" : circumstances;
                m.put("circumstances", c.length() > 80 ? c.substring(0, 80) : c);
                rows.add(m);
            }, propertyId);
        }
        return Map.of("title", "Report of Lost, Stolen, Damaged or Destroyed Property (Appendix 75)", "rows", rows);
    }

    private List<Map<String, Object>> disposals(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        return jdbc.queryForList("""
                select d.*, p.tag_number, p.assigned_date, p.acquisition_cost, coalesce(pi.name, di.name) item_name,
                       coalesce(pi.unit_of_measure, di.unit_of_measure) unit
                from disposal_requests d left join properties p on p.id = d.property_id left join items pi on pi.id = p.item_id
                left join items di on di.id = d.item_id where d.id in (""" + in + ") order by d.id", ids.toArray());
    }

    // ───────────────────────── helpers ─────────────────────────

    private long n(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private static String ucfirst(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String money(BigDecimal v) {
        return String.format(Locale.US, "%,.2f", v);
    }
}
