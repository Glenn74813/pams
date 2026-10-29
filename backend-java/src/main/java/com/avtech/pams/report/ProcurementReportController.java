package com.avtech.pams.report;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Procurement Reports (TOR §19): APP Report, Procurement Monitoring Report (summary and GPPB
 * format), BAC Report and Award Report — on screen and as the same Excel export as the Laravel app.
 */
@RestController
@RequestMapping("/api/reports/procurement")
@PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_procurement', 'approve_procurement', 'manage_bac_meetings')")
public class ProcurementReportController {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Db db;

    public ProcurementReportController(JdbcTemplate jdbc, AuditService audit, Db db) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.db = db;
    }

    private static int year(Integer fy) {
        return fy == null ? LocalDate.now(Times.ZONE).getYear() : fy;
    }

    /** APP Report — consolidated plan vs. utilization for a fiscal year. */
    @GetMapping("/app")
    public Map<String, Object> app(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        int year = year(fy);
        List<Map<String, Object>> rows = jdbc.query("""
                select coalesce(d.name, '-') department, ai.general_description, coalesce(nullif(ai.fund_source, ''), '-') fund, ai.procurement_mode,
                       ai.quantity, coalesce(ai.estimated_budget, 0) est, 
                       (select coalesce(sum(pri.estimated_total), 0) from purchase_request_items pri join purchase_requests pr on pr.id = pri.purchase_request_id where pri.app_item_id = ai.id and pr.deleted_at is null and pr.status = 'approved') used, ai.status
                from app_items ai join apps a on a.id = ai.app_id left join departments d on d.id = ai.department_id
                where a.deleted_at is null and a.fiscal_year = ? order by ai.id""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("department", rs.getString(1));
            m.put("description", rs.getString(2));
            m.put("source_of_funds", rs.getString(3));
            m.put("procurement_mode", rs.getString(4));
            m.put("quantity", rs.getInt(5));
            m.put("estimated_budget", rs.getBigDecimal(6));
            m.put("utilized_amount", rs.getBigDecimal(7));
            m.put("remaining", rs.getBigDecimal(6).subtract(rs.getBigDecimal(7)));
            m.put("status", rs.getString(8));
            return m;
        }, year);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fiscal_year", year);
        out.put("total_estimated", sum(rows, "estimated_budget"));
        out.put("total_utilized", sum(rows, "utilized_amount"));
        out.put("rows", rows);
        return out;
    }

    /** Procurement Monitoring Report — counts and values by stage/status. */
    @GetMapping("/pmr")
    public Map<String, Object> pmr(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        int year = year(fy);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fiscal_year", year);
        out.put("purchase_requests", block("purchase_requests", "fiscal_year = ?", "total_amount", year));
        out.put("rfqs", block("rfqs", db.year("created_at") + " = ?", null, year));
        out.put("biddings", block("biddings", db.year("created_at") + " = ?", null, year));
        out.put("purchase_orders", block("purchase_orders", db.year("created_at") + " = ?", "total_amount", year));
        out.put("contracts", block("contracts", db.year("created_at") + " = ?", "amount", year));
        return out;
    }

    private Map<String, Object> block(String table, String where, String valueCol, int year) {
        Map<String, Object> b = new LinkedHashMap<>();
        Map<String, Long> by = new LinkedHashMap<>();
        jdbc.query("select status, count(*) from " + table + " where deleted_at is null and " + where + " group by status order by status",
                rs -> { by.put(rs.getString(1), rs.getLong(2)); }, year);
        b.put("total", by.values().stream().mapToLong(Long::longValue).sum());
        b.put("by_status", by);
        if (valueCol != null) {
            BigDecimal v = jdbc.queryForObject("select coalesce(sum(" + valueCol + "), 0) from " + table + " where deleted_at is null and " + where,
                    BigDecimal.class, year);
            b.put("value", v.setScale(2, RoundingMode.HALF_UP));
        }
        return b;
    }

    /**
     * GPPB-prescribed Procurement Monitoring Report — one row per awarded procurement with its mode,
     * ABC, contract cost and milestone dates (posting → opening → NOA → contract signing → NTP →
     * acceptance). Contract signing and acceptance come from the contract and the accepted IARs.
     */
    @GetMapping("/pmr-gppb")
    public Map<String, Object> pmrGppb(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        int year = year(fy);
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("""
                select coalesce(pr.pr_number, b.bidding_number), b.title, coalesce(d.name, '-'), coalesce(b.abc, 0), coalesce(b.awarded_amount, 0),
                       coalesce(s.name, '-'), b.itb_posted_at, cast(b.bid_opening_date as date), b.noa_date, b.ntp_date, b.contract_recommendation,
                       (select min(c.signed_date) from contracts c where c.bidding_id = b.id and c.deleted_at is null),
                       (select cast(max(r.accepted_at) as date) from inspection_acceptance_reports r join purchase_orders po on po.id = r.purchase_order_id
                        where po.bidding_id = b.id and po.deleted_at is null and r.acceptance_status in ('accepted', 'partially_accepted')),
                       (select string_agg(f.fund_source, ', ') from (select distinct pri.fund_source from purchase_request_items pri where pri.purchase_request_id = pr.id) f)
                from biddings b left join purchase_requests pr on pr.id = b.purchase_request_id left join departments d on d.id = pr.department_id
                left join suppliers s on s.id = b.awarded_supplier_id
                where b.deleted_at is null and b.status = 'awarded' and""" + " " + db.year("coalesce(b.awarded_at, b.updated_at)") + " = ?", rs -> {
            rows.add(pmrRow(rs.getString(1), rs.getString(2), rs.getString(3), "Competitive Bidding", rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getString(6),
                    rs.getDate(7), rs.getDate(8), rs.getDate(9), rs.getDate(12), rs.getDate(10), rs.getDate(13), rs.getString(14),
                    rs.getBoolean(11) ? "For contract" : null));
        }, year);
        jdbc.query("""
                select coalesce(pr.pr_number, r.rfq_number), r.title, coalesce(d.name, '-'), coalesce(r.procurement_method, 'Alternative (RFQ)'), coalesce(r.abc, 0),
                       coalesce((select max(q.total_amount) from rfq_quotations q where q.rfq_id = r.id and q.is_winner = """ + db.t() + " " + """
                       ), 0),
                       coalesce(s.name, '-'), cast(r.created_at as date), cast(r.deadline as date), r.noa_date, r.ntp_date,
                       (select min(c.signed_date) from contracts c where c.rfq_id = r.id and c.deleted_at is null),
                       (select cast(max(i.accepted_at) as date) from inspection_acceptance_reports i join purchase_orders po on po.id = i.purchase_order_id
                        where po.rfq_id = r.id and po.deleted_at is null and i.acceptance_status in ('accepted', 'partially_accepted')),
                       (select string_agg(f.fund_source, ', ') from (select distinct pri.fund_source from purchase_request_items pri where pri.purchase_request_id = pr.id) f)
                from rfqs r left join purchase_requests pr on pr.id = r.purchase_request_id left join departments d on d.id = pr.department_id
                left join suppliers s on s.id = r.awarded_supplier_id
                where r.deleted_at is null and r.status = 'awarded' and""" + " " + db.year("coalesce(r.awarded_at, r.updated_at)") + " = ?", rs -> {
            rows.add(pmrRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7),
                    rs.getDate(8), rs.getDate(9), rs.getDate(10), rs.getDate(12), rs.getDate(11), rs.getDate(13), rs.getString(14), null));
        }, year);
        rows.sort(Comparator.comparing(r -> r.get("date_noa") == null ? "" : r.get("date_noa").toString()));
        BigDecimal abc = sum(rows, "abc"), cost = sum(rows, "contract_cost");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fiscal_year", year);
        out.put("total_rows", rows.size());
        out.put("total_abc", abc);
        out.put("total_cost", cost);
        out.put("savings", abc.subtract(cost));
        out.put("rows", rows);
        return out;
    }

    private static Map<String, Object> pmrRow(String code, String project, String endUser, String mode, BigDecimal abc, BigDecimal cost, String awardee,
                                              Date advertised, Date opened, Date noa, Date contract, Date ntp, Date accepted, String funds, String remarks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("project", project);
        m.put("end_user", endUser);
        m.put("mode", mode);
        m.put("abc", abc);
        m.put("contract_cost", cost);
        m.put("awardee", awardee);
        m.put("date_advertised", d(advertised));
        m.put("date_opened", d(opened));
        m.put("date_noa", d(noa));
        m.put("date_contract", d(contract));
        m.put("date_ntp", d(ntp));
        m.put("date_accepted", d(accepted));
        m.put("source_of_funds", funds == null || funds.isBlank() ? "-" : funds);
        m.put("remarks", remarks);
        return m;
    }

    /** BAC Report — meetings, resolutions and evaluations for a fiscal year. */
    @GetMapping("/bac")
    public Map<String, Object> bac(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        int year = year(fy);
        List<Map<String, Object>> meetings = jdbc.query("""
                select m.id, m.meeting_number, m.title, m.meeting_date, m.status, (select count(*) from bac_meeting_attendees a where a.bac_meeting_id = m.id)
                from bac_meetings m where m.deleted_at is null and""" + " " + db.year("m.meeting_date") + " = ? order by m.meeting_date", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong(1));
            m.put("meeting_number", rs.getString(2));
            m.put("title", rs.getString(3));
            m.put("meeting_date", rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime().toLocalDate());
            m.put("status", rs.getString(5));
            m.put("attendees_count", rs.getLong(6));
            return m;
        }, year);
        List<Map<String, Object>> resolutions = jdbc.query("""
                select id, resolution_number, title, resolution_type, status from bac_resolutions
                where deleted_at is null and""" + " " + db.year("created_at") + " = ? order by created_at", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong(1));
            m.put("resolution_number", rs.getString(2));
            m.put("title", rs.getString(3));
            m.put("resolution_type", rs.getString(4));
            m.put("status", rs.getString(5));
            return m;
        }, year);
        Map<String, Long> byType = new LinkedHashMap<>();
        resolutions.forEach(r -> byType.merge(String.valueOf(r.get("resolution_type")), 1L, Long::sum));
        Map<String, Long> byStage = new LinkedHashMap<>();
        jdbc.query("select stage, count(*) from bac_evaluations where " + db.year("created_at") + " = ? group by stage order by stage",
                rs -> { byStage.put(rs.getString(1), rs.getLong(2)); }, year);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fiscal_year", year);
        out.put("meetings", Map.of("total", meetings.size(), "list", meetings));
        out.put("resolutions", Map.of("total", resolutions.size(), "by_type", byType, "list", resolutions));
        out.put("evaluations", Map.of("total", byStage.values().stream().mapToLong(Long::longValue).sum(), "by_stage", byStage));
        return out;
    }

    /** Award Report — awarded biddings and RFQs. */
    @GetMapping("/award")
    public Map<String, Object> award(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        int year = year(fy);
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("""
                select b.bidding_number, b.title, coalesce(s.name, '-'), coalesce(b.awarded_amount, 0), b.noa_number, b.ntp_number
                from biddings b left join suppliers s on s.id = b.awarded_supplier_id
                where b.deleted_at is null and b.status = 'awarded' and""" + " " + db.year("coalesce(b.awarded_at, b.updated_at)") + " = ? order by b.id",
                rs -> { rows.add(awardRow("Competitive Bidding", rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getString(6))); }, year);
        jdbc.query("""
                select r.procurement_method, r.rfq_number, r.title, coalesce(s.name, '-'),
                       coalesce((select max(q.total_amount) from rfq_quotations q where q.rfq_id = r.id and q.is_winner = """ + db.t() + " " + """
                       ), 0), r.noa_number, r.ntp_number
                from rfqs r left join suppliers s on s.id = r.awarded_supplier_id
                where r.deleted_at is null and r.status = 'awarded' and""" + " " + db.year("coalesce(r.awarded_at, r.updated_at)") + " = ? order by r.id",
                rs -> { rows.add(awardRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7))); }, year);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fiscal_year", year);
        out.put("total_awards", rows.size());
        out.put("total_value", sum(rows, "amount"));
        out.put("rows", rows);
        return out;
    }

    private static Map<String, Object> awardRow(String type, String ref, String title, String supplier, BigDecimal amount, String noa, String ntp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("reference", ref);
        m.put("title", title);
        m.put("supplier", supplier);
        m.put("amount", amount);
        m.put("noa", noa);
        m.put("ntp", ntp);
        return m;
    }

    /** Excel export for any of the reports, laid out as the Laravel export: ?type=app|pmr|pmr_gppb|bac|award&fiscal_year= */
    @GetMapping("/export")
    @SuppressWarnings("unchecked")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "app") String type, @RequestParam(name = "fiscal_year", required = false) Integer fy) {
        int year = year(fy);
        String title;
        List<String> headers;
        List<List<Object>> rows = new ArrayList<>();
        switch (type) {
            case "pmr" -> {
                Map<String, Object> d = pmr(year);
                title = "Procurement Monitoring Report";
                headers = List.of("Category", "Total", "Value");
                Map<String, String> labels = new LinkedHashMap<>();
                labels.put("purchase_requests", "Purchase Requests");
                labels.put("rfqs", "RFQs");
                labels.put("biddings", "Biddings");
                labels.put("purchase_orders", "Purchase Orders");
                labels.put("contracts", "Contracts");
                labels.forEach((k, l) -> {
                    Map<String, Object> b = (Map<String, Object>) d.get(k);
                    List<Object> r = new ArrayList<>();
                    r.add(l);
                    r.add(b.get("total"));
                    r.add(b.getOrDefault("value", ""));
                    rows.add(r);
                });
            }
            case "pmr_gppb" -> {
                Map<String, Object> d = pmrGppb(year);
                title = "Procurement Monitoring Report (GPPB Format)";
                headers = List.of("Code (PAP)", "Procurement Program/Project", "PMO/End-User", "Mode of Procurement", "ABC", "Contract Cost", "Awardee",
                        "Date Advertised/Posted", "Date of Bid Opening", "Date of NOA", "Date of Contract Signing", "Date of NTP", "Date of Acceptance",
                        "Source of Funds", "Remarks");
                for (Map<String, Object> x : (List<Map<String, Object>>) d.get("rows")) {
                    rows.add(values(x, "code", "project", "end_user", "mode", "abc", "contract_cost", "awardee", "date_advertised", "date_opened", "date_noa",
                            "date_contract", "date_ntp", "date_accepted", "source_of_funds", "remarks"));
                }
            }
            case "bac" -> {
                Map<String, Object> d = bac(year);
                title = "BAC Report";
                headers = List.of("Item", "Count");
                rows.add(List.of("Meetings", ((Map<String, Object>) d.get("meetings")).get("total")));
                rows.add(List.of("Resolutions", ((Map<String, Object>) d.get("resolutions")).get("total")));
                rows.add(List.of("Evaluations", ((Map<String, Object>) d.get("evaluations")).get("total")));
            }
            case "award" -> {
                Map<String, Object> d = award(year);
                title = "Award Report";
                headers = List.of("Type", "Reference", "Title", "Supplier", "Amount", "NOA", "NTP");
                for (Map<String, Object> x : (List<Map<String, Object>>) d.get("rows")) {
                    rows.add(values(x, "type", "reference", "title", "supplier", "amount", "noa", "ntp"));
                }
            }
            default -> {
                type = "app";
                Map<String, Object> d = app(year);
                title = "Annual Procurement Plan Report";
                headers = List.of("Department", "Description", "Source of Funds", "Mode", "Qty", "Estimated", "Utilized", "Remaining", "Status");
                for (Map<String, Object> x : (List<Map<String, Object>>) d.get("rows")) {
                    rows.add(values(x, "department", "description", "source_of_funds", "procurement_mode", "quantity", "estimated_budget", "utilized_amount",
                            "remaining", "status"));
                }
            }
        }
        // Same sheet as PhpSpreadsheet produced: title in A1 (bold 13), headers on row 3 (bold), data from row 4, auto-sized columns.
        Xlsx x = new Xlsx(title.length() > 28 ? title.substring(0, 28) : title);
        x.set("A1", title + " — FY " + year);
        x.style("A1").bold().size(13);
        String last = Xlsx.col(Math.max(1, headers.size()));
        for (int i = 0; i < headers.size(); i++) x.set(Xlsx.col(i + 1) + 3, headers.get(i));
        x.style("A3:" + last + "3").bold();
        int r = 4;
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                Object v = row.get(i);
                if (v != null && !"".equals(v)) x.set(Xlsx.col(i + 1) + r, v instanceof LocalDate ? v.toString() : v);
            }
            r++;
        }
        for (int i = 1; i <= headers.size(); i++) x.autoSize(Xlsx.col(i));
        String filename = type.toUpperCase() + "_report_" + year + ".xlsx";
        audit.record("exported", null, null, "Exported " + title + " — FY " + year, null, Map.of("type", type, "fiscal_year", year));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(x.bytes());
    }

    private static List<Object> values(Map<String, Object> m, String... keys) {
        List<Object> out = new ArrayList<>();
        for (String k : keys) out.add(m.get(k));
        return out;
    }

    private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> (BigDecimal) r.get(key)).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static String d(Date v) {
        return v == null ? null : v.toLocalDate().toString();
    }
}
