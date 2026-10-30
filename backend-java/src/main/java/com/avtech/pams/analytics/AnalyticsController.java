package com.avtech.pams.analytics;

import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import com.avtech.pams.monitoring.ProcurementWatch;
import com.avtech.pams.procurement.PurchaseRequest;
import com.avtech.pams.user.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dashboard: what is waiting on the caller, then inventory, asset, procurement and supplier
 * figures — each section only for people whose role covers that area.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final List<String> PR_STEPS = PurchaseRequest.CHAIN.stream().map(s -> s[2]).toList();

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final ProcurementWatch watch;
    private final Db db;
    private final com.avtech.pams.setting.SettingService settings;

    public AnalyticsController(JdbcTemplate jdbc, CurrentUser current, ProcurementWatch watch, Db db, com.avtech.pams.setting.SettingService settings) {
        this.settings = settings;
        this.jdbc = jdbc;
        this.db = db;
        this.current = current;
        this.watch = watch;
    }

    /** Something waiting on the caller; {@code path} opens the page where it is done. */
    public record Todo(String key, String label, long count, String path) {
    }

    public record Ranked(String name, String code, long count, BigDecimal amount) {
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(name = "fiscal_year", required = false) Integer fiscalYear) {
        User me = current.user();
        int year = fiscalYear == null ? LocalDate.now(Times.ZONE).getYear() : fiscalYear;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fiscal_year", year);
        out.put("me", mine(me));
        out.put("todo", todo(me));
        // view_analytics opens every section, for management who follow the figures without working in the modules.
        if (current.any("view_analytics", "view_monitoring", "manage_warehouse", "view_warehouse", "view_reports")) out.put("inventory", inventory());
        if (current.any("view_analytics", "view_properties")) out.put("assets", assets());
        if (current.any("view_analytics", "view_procurement", "view_procurement_planning", "manage_app", "view_bidding", "view_rfq", "view_contracts")) {
            out.put("procurement", procurement(year));
        }
        if (current.any("view_analytics", "view_suppliers")) out.put("suppliers", suppliers(year));
        return out;
    }

    private Map<String, Object> mine(User me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open_requests", count("select count(*) from requests where requested_by = ? and status in ('pending', 'approved', 'partially_fulfilled')", me.getId()));
        m.put("open_purchase_requests", count("select count(*) from purchase_requests where requested_by = ? and deleted_at is null and status in ('draft', 'submitted', 'under_review', 'returned')", me.getId()));
        m.put("assets", count("select count(*) from properties where assigned_to = ? and status <> 'disposed'", me.getId()));
        m.put("assets_value", money("select coalesce(sum(acquisition_cost), 0) from properties where assigned_to = ? and status <> 'disposed'", me.getId()));
        return m;
    }

    private List<Todo> todo(User me) {
        List<Todo> t = new ArrayList<>();
        List<String> steps = me.isSuperAdmin() || me.hasPermission("approve_procurement") ? PR_STEPS : PR_STEPS.stream().filter(me::hasPermission).toList();
        if (!steps.isEmpty()) {
            String in = String.join(",", steps.stream().map(s -> "?").toList());
            List<Object> args = new ArrayList<>(steps);
            args.add(me.getId());
            t.add(new Todo("pr_approvals", "Purchase requests awaiting your approval", count("""
                    select count(*) from purchase_requests p where p.deleted_at is null and p.status in ('submitted', 'under_review')
                      and exists (select 1 from purchase_request_approvals a where a.purchase_request_id = p.id and a.step = p.current_step
                                  and a.status = 'pending' and a.required_permission in (""" + in + ")) and p.requested_by <> ?", args.toArray()),
                    "/purchase-requests"));
        }
        if (current.any("approve_requests")) t.add(new Todo("requests", "Supply requests to approve", count("select count(*) from requests where status = 'pending'"), "/requests"));
        if (current.any("release_stock", "manage_warehouse")) t.add(new Todo("release", "Approved requests to issue", count("select count(*) from requests where status in ('approved', 'partially_fulfilled')"), "/requests"));
        if (current.any("approve_adjustments")) t.add(new Todo("adjustments", "Stock adjustments to approve", count("select count(*) from stock_adjustments where status = 'pending'"), "/stock-adjustments"));
        if (current.any("approve_disposal")) t.add(new Todo("disposal", "Disposal requests to approve", count("select count(*) from disposal_requests where status = 'submitted'"), "/disposal"));
        if (current.any("accept_delivery")) t.add(new Todo("iar", "Inspection reports awaiting acceptance", count("select count(*) from inspection_acceptance_reports where acceptance_status = 'pending'"), "/receiving"));
        if (current.any("approve_adjustments", "manage_warehouse")) t.add(new Todo("counts", "Physical counts to verify", count("select count(*) from physical_inventory_counts where status = 'completed'"), "/physical"));
        if (current.any("edit_properties")) t.add(new Todo("maintenance", "Asset maintenance overdue", count("select count(*) from property_maintenance where status = 'scheduled' and scheduled_date < ?", Date.valueOf(LocalDate.now(Times.ZONE))), "/maintenance"));
        if (current.any("assign_properties", "transfer_properties")) t.add(new Todo("clearance", "Clearances waiting for property turnover", count("select count(*) from clearance_certificates where status = 'pending'"), "/clearance"));
        if (current.any("view_gate_pass")) t.add(new Todo("gate_pass", "Gate passes past their return date", count("select count(*) from gate_passes where status in ('released', 'overdue') and expected_return_date < ?", Date.valueOf(LocalDate.now(Times.ZONE))), "/gate-pass"));
        return t.stream().filter(x -> x.count() > 0).toList();
    }

    private Map<String, Object> inventory() {
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_items", count("select count(*) from items"));
        m.put("low_stock", count("select count(*) from items where min_stock > 0 and current_stock > 0 and current_stock <= min_stock"));
        m.put("out_of_stock", count("select count(*) from items where current_stock <= 0"));
        m.put("total_value", money("select coalesce(sum(case when current_stock > 0 then current_stock else 0 end * coalesce(weighted_average_cost, 0)), 0) from items"));
        m.put("pending_requests", count("select count(*) from requests where status = 'pending'"));
        m.put("requests_today", count("select count(*) from requests where request_date = ?", Date.valueOf(today)));
        m.put("expiry_warning_days", settings.fefoWarningDays());
        m.put("expiring_lots", count("select count(*) from stocks where quantity > 0 and expiration_date between ? and ?", Date.valueOf(today), Date.valueOf(today.plusDays(settings.fefoWarningDays()))));
        m.put("expired_lots", count("select count(*) from stocks where quantity > 0 and expiration_date < ?", Date.valueOf(today)));
        m.put("top_items", jdbc.query("""
                select i.name, i.code, sum(abs(l.quantity)) qty, sum(abs(l.quantity) * coalesce(i.weighted_average_cost, 0)) amt
                from inventory_logs l join items i on i.id = l.item_id
                where l.action = 'item_issued' and l.created_at >= ? group by i.id, i.name, i.code order by qty desc, i.id offset 0 rows fetch next 8 rows only""",
                (rs, n) -> new Ranked(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4).setScale(2, RoundingMode.HALF_UP)),
                java.sql.Timestamp.valueOf(today.minusDays(30).atStartOfDay())));
        m.put("by_department", jdbc.query("""
                select d.name, null, count(*), coalesce(sum(v.amount), 0)
                from requests r join departments d on d.id = r.department_id
                left join (select ri.request_id, sum(coalesce(ri.approved_quantity, ri.quantity) * coalesce(i.weighted_average_cost, 0)) amount
                           from request_items ri join items i on i.id = ri.item_id group by ri.request_id) v on v.request_id = r.id
                where r.request_date >= ? group by d.name order by 3 desc, d.name offset 0 rows fetch next 8 rows only""",
                (rs, n) -> new Ranked(rs.getString(1), null, rs.getLong(3), rs.getBigDecimal(4).setScale(2, RoundingMode.HALF_UP)),
                Date.valueOf(today.minusDays(90))));
        m.put("by_category", shares("""
                select coalesce(c.name, 'Uncategorized'), count(*), coalesce(sum(case when i.current_stock > 0 then i.current_stock else 0 end * coalesce(i.weighted_average_cost, 0)), 0)
                from items i left join categories c on c.id = i.category_id group by c.name"""));
        m.put("trend", trend(today));
        return m;
    }

    /** Slices for a pie: the largest six by amount, the rest folded into "Other". */
    private List<Ranked> shares(String sql, Object... args) {
        List<Ranked> all = new java.util.ArrayList<>(jdbc.query(sql, (rs, n) -> new Ranked(rs.getString(1), null, rs.getLong(2),
                rs.getBigDecimal(3).setScale(2, RoundingMode.HALF_UP)), args));
        all.sort((a, b) -> b.amount().compareTo(a.amount()));
        if (all.size() <= 7) return all;
        List<Ranked> top = new java.util.ArrayList<>(all.subList(0, 6));
        List<Ranked> rest = all.subList(6, all.size());
        top.add(new Ranked("Other", null, rest.stream().mapToLong(Ranked::count).sum(), rest.stream().map(Ranked::amount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        return top;
    }

    public record Month(String month, BigDecimal received, BigDecimal issued, long receipts, long issues) {
    }

    /** Stock received and issued per month over the last twelve, valued at each item's current average cost. */
    private List<Month> trend(LocalDate today) {
        java.time.YearMonth first = java.time.YearMonth.from(today).minusMonths(11);
        Map<String, BigDecimal[]> amounts = new LinkedHashMap<>();
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (int k = 0; k < 12; k++) {
            String key = first.plusMonths(k).toString();
            amounts.put(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            counts.put(key, new long[]{0, 0});
        }
        jdbc.query("select " + db.year("l.created_at") + ", " + db.month("l.created_at") + """
                , l.action, count(*), coalesce(sum(abs(l.quantity) * coalesce(i.weighted_average_cost, 0)), 0)
                from inventory_logs l join items i on i.id = l.item_id
                where l.action in ('stock_in', 'item_issued') and l.created_at >= ?
                group by""" + " " + db.year("l.created_at") + ", " + db.month("l.created_at") + ", l.action", rs -> {
            String key = java.time.YearMonth.of(rs.getInt(1), rs.getInt(2)).toString();
            BigDecimal[] a = amounts.get(key);
            if (a == null) return;
            int idx = "stock_in".equals(rs.getString(3)) ? 0 : 1;
            a[idx] = a[idx].add(rs.getBigDecimal(5));
            counts.get(key)[idx] += rs.getLong(4);
        }, java.sql.Timestamp.valueOf(first.atDay(1).atStartOfDay()));
        return amounts.entrySet().stream().map(e -> new Month(e.getKey(), e.getValue()[0].setScale(2, RoundingMode.HALF_UP),
                e.getValue()[1].setScale(2, RoundingMode.HALF_UP), counts.get(e.getKey())[0], counts.get(e.getKey())[1])).toList();
    }

    private Map<String, Object> assets() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", count("select count(*) from properties where status <> 'disposed'"));
        m.put("assigned", count("select count(*) from properties where status = 'assigned'"));
        m.put("available", count("select count(*) from properties where status = 'available'"));
        m.put("under_repair", count("select count(*) from properties where status = 'under_repair'"));
        m.put("acquisition_cost", money("select coalesce(sum(acquisition_cost), 0) from properties where status <> 'disposed'"));
        m.put("book_value", money("select coalesce(sum(coalesce(current_book_value, acquisition_cost)), 0) from properties where status <> 'disposed'"));
        m.put("disposal_pending", count("select count(*) from disposal_requests where status in ('submitted', 'approved')"));
        m.put("by_category", shares("""
                select coalesce(c.name, 'Uncategorized'), count(*), coalesce(sum(p.acquisition_cost), 0)
                from properties p join items i on i.id = p.item_id left join categories c on c.id = i.category_id
                where p.status <> 'disposed' group by c.name"""));
        m.put("maintenance_overdue", count("select count(*) from property_maintenance where status = 'scheduled' and scheduled_date < ?", Date.valueOf(LocalDate.now(Times.ZONE))));
        return m;
    }

    private Map<String, Object> procurement(int year) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Long> active = new LinkedHashMap<>();
        active.put("purchase_requests", count("select count(*) from purchase_requests where deleted_at is null and status in ('submitted', 'under_review')"));
        active.put("rfqs", count("select count(*) from rfqs where deleted_at is null and status in ('issued', 'closed')"));
        active.put("biddings", count("select count(*) from biddings where deleted_at is null and status in ('pre_procurement', 'invitation', 'bid_opening', 'evaluation')"));
        active.put("purchase_orders", count("select count(*) from purchase_orders where deleted_at is null and status in ('sent', 'confirmed', 'partially_received')"));
        active.put("contracts", count("select count(*) from contracts where deleted_at is null and status = 'active'"));
        m.put("active", active);
        BigDecimal est = money("select coalesce(sum(ai.estimated_budget), 0) from app_items ai join apps a on a.id = ai.app_id where a.deleted_at is null and a.fiscal_year = ?", year);
        BigDecimal used = money("""
                select coalesce(sum(pri.estimated_total), 0) from purchase_request_items pri
                join purchase_requests pr on pr.id = pri.purchase_request_id join app_items ai on ai.id = pri.app_item_id join apps a on a.id = ai.app_id
                where a.deleted_at is null and a.fiscal_year = ? and pr.deleted_at is null and pr.status = 'approved'""", year);
        m.put("app_utilization", Map.of("estimated", est, "utilized", used, "remaining", est.subtract(used),
                "rate", est.signum() == 0 ? BigDecimal.ZERO : used.multiply(BigDecimal.valueOf(100)).divide(est, 1, RoundingMode.HALF_UP)));
        Map<String, Long> prs = new LinkedHashMap<>();
        jdbc.query("select status, count(*) from purchase_requests where deleted_at is null and fiscal_year = ? group by status order by 2 desc",
                rs -> { prs.put(rs.getString(1), rs.getLong(2)); }, year);
        m.put("pr_by_status", prs);
        m.put("awards", Map.of(
                "biddings", count("select count(*) from biddings where deleted_at is null and status = 'awarded' and " + db.year("coalesce(awarded_at, updated_at)") + " = ?", year),
                "rfqs", count("select count(*) from rfqs where deleted_at is null and status = 'awarded' and " + db.year("coalesce(awarded_at, updated_at)") + " = ?", year),
                "po_value", money("select coalesce(sum(total_amount), 0) from purchase_orders where deleted_at is null and status <> 'cancelled' and " + db.year("order_date") + " = ?", year)));
        List<ProcurementWatch.Entry> w = watch.scan();
        m.put("watch", Map.of("overdue", w.stream().filter(e -> ProcurementWatch.OVERDUE.equals(e.severity())).count(),
                "due_soon", w.stream().filter(e -> ProcurementWatch.DUE_SOON.equals(e.severity())).count(),
                "top", w.stream().limit(5).toList()));
        return m;
    }

    private Map<String, Object> suppliers(int year) {
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", count("select count(*) from suppliers where is_active = " + db.t()));
        m.put("total", count("select count(*) from suppliers"));
        m.put("expiring_compliance", count("""
                select count(distinct supplier_id) from supplier_compliance_documents d where d.expiry_date between ? and ?
                  and not exists (select 1 from supplier_compliance_documents n where n.supplier_id = d.supplier_id
                                  and n.document_type = d.document_type and n.expiry_date > d.expiry_date)""",
                Date.valueOf(today), Date.valueOf(today.plusDays(30))));
        m.put("top_awarded", jdbc.query("""
                select s.name, s.vendor_code, count(*), sum(po.total_amount) from purchase_orders po join suppliers s on s.id = po.supplier_id
                where po.deleted_at is null and po.status <> 'cancelled' and""" + " " + db.year("po.order_date") + " " + """
                 = ?
                group by s.id, s.name, s.vendor_code order by 4 desc, s.id offset 0 rows fetch next 5 rows only""",
                (rs, n) -> new Ranked(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)), year));
        m.put("top_rated", jdbc.query("""
                select s.name, s.vendor_code, count(*), round(avg(e.overall_rating), 2) from supplier_evaluations e join suppliers s on s.id = e.supplier_id
                group by s.id, s.name, s.vendor_code order by 4 desc, 3 desc, s.id offset 0 rows fetch next 5 rows only""",
                (rs, n) -> new Ranked(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4))));
        return m;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
