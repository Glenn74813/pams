package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project Procurement Management Plan (PPMP) in the GPPB-prescribed format: one PPMP, or every PPMP
 * of an end-user unit for a fiscal year. Lines carry a single quarter (Q1–Q4) as their schedule, so
 * the Jan–Dec milestone columns are marked for the three months of that quarter.
 */
@Component
public class PpmpGppbForm {

    private static final String FONT = "Arial";
    public static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final String[] MONTH_LABELS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public PpmpGppbForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    /** The months (1-based) a Q1..Q4 schedule covers; empty when unscheduled. */
    static int[] months(String quarter) {
        if (quarter == null || !quarter.matches("Q[1-4]")) return new int[0];
        int q = quarter.charAt(1) - '0';
        return new int[]{q * 3 - 2, q * 3 - 1, q * 3};
    }

    public Map<String, Object> data(Long ppmpId, Long departmentId, Integer fiscalYear, String status) {
        List<Map<String, Object>> ppmps;
        if (ppmpId != null) {
            ppmps = jdbc.queryForList("""
                    select p.id, p.ppmp_number, p.title, p.status, p.fiscal_year, p.department_id, p.prepared_by, p.approved_by, p.approved_at
                    from ppmps p where p.id = ? and p.deleted_at is null""", ppmpId);
            if (ppmps.isEmpty()) throw ApiException.notFound("PPMP");
        } else {
            if (departmentId == null) throw ApiException.validation("department_id", "Choose a PPMP, or an end-user unit and fiscal year.");
            if (fiscalYear == null) throw ApiException.validation("fiscal_year", "Choose the fiscal year.");
            boolean byStatus = status != null && !status.isBlank();
            String sql = """
                    select p.id, p.ppmp_number, p.title, p.status, p.fiscal_year, p.department_id, p.prepared_by, p.approved_by, p.approved_at
                    from ppmps p where p.department_id = ? and p.fiscal_year = ? and p.deleted_at is null"""
                    + (byStatus ? " and p.status = ?" : "") + " order by p.ppmp_number";
            ppmps = byStatus ? jdbc.queryForList(sql, departmentId, fiscalYear, status.trim()) : jdbc.queryForList(sql, departmentId, fiscalYear);
        }
        Long dept = ppmpId != null ? ((Number) ppmps.get(0).get("department_id")).longValue() : departmentId;
        int year = ppmpId != null ? ((Number) ppmps.get(0).get("fiscal_year")).intValue() : fiscalYear;
        List<String> deptRow = jdbc.queryForList("select name from departments where id = ?", String.class, dept);
        if (deptRow.isEmpty()) throw ApiException.notFound("Department");

        List<Long> ids = ppmps.stream().map(p -> ((Number) p.get("id")).longValue()).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (!ids.isEmpty()) {
            String in = String.join(",", ids.stream().map(i -> "?").toList());
            List<Map<String, Object>> items = jdbc.queryForList("select i.*, p.ppmp_number from ppmp_items i join ppmps p on p.id = i.ppmp_id where i.ppmp_id in (" + in
                    + ") order by p.ppmp_number, i.id", ids.toArray());
            for (Map<String, Object> it : items) {
                Map<String, Object> r = new LinkedHashMap<>();
                int qty = ((Number) it.get("quantity")).intValue();
                String unit = (String) it.get("unit");
                BigDecimal budget = (BigDecimal) it.get("estimated_total");
                r.put("ppmp_number", it.get("ppmp_number"));
                r.put("code", it.get("code"));
                r.put("general_description", it.get("general_description"));
                r.put("specifications", it.get("specifications"));
                r.put("quantity", qty);
                r.put("unit", unit);
                r.put("quantity_size", qty + (unit == null || unit.isBlank() ? "" : " " + unit));
                r.put("estimated_unit_cost", it.get("estimated_unit_cost"));
                r.put("estimated_budget", budget);
                r.put("procurement_mode", it.get("procurement_mode"));
                r.put("procurement_category", it.get("procurement_category"));
                r.put("schedule", it.get("schedule"));
                Set<Integer> on = new LinkedHashSet<>();
                for (int m : months((String) it.get("schedule"))) on.add(m);
                for (int m = 1; m <= 12; m++) r.put(MONTHS[m - 1], on.contains(m) ? "X" : null);
                r.put("source_of_funds", it.get("fund_source"));
                r.put("remarks", it.get("remarks"));
                rows.add(r);
                total = total.add(budget);
            }
        }

        // Prepared by the end-user who drew up the plan; approved by the approver recorded on it, or the head of agency.
        String preparedBy = commonUser(ppmps, "prepared_by");
        String approvedBy = commonUser(ppmps, "approved_by");
        Signatories.Signatory head = signatories.resolve("head_of_agency");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Project Procurement Management Plan (PPMP)");
        out.put("fiscal_year", year);
        out.put("department_id", dept);
        out.put("department", deptRow.get(0));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> p : ppmps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.get("id"));
            m.put("ppmp_number", p.get("ppmp_number"));
            m.put("title", p.get("title"));
            m.put("status", p.get("status"));
            list.add(m);
        }
        out.put("ppmps", list);
        out.put("total_estimated_budget", total);
        out.put("prepared_by", preparedBy);
        out.put("approved_by", approvedBy != null ? approvedBy : head.name());
        out.put("approved_by_designation", approvedBy != null ? "Head of Office / Approving Authority" : head.designation());
        out.put("rows", rows);
        return out;
    }

    private String commonUser(List<Map<String, Object>> ppmps, String column) {
        Set<Long> users = new LinkedHashSet<>();
        for (Map<String, Object> p : ppmps) {
            Object v = p.get(column);
            if (v == null) return null;
            users.add(((Number) v).longValue());
        }
        if (users.size() != 1) return null;
        List<String> n = jdbc.queryForList("select name from users where id = ?", String.class, users.iterator().next());
        return n.isEmpty() ? null : n.get(0);
    }

    @SuppressWarnings("unchecked")
    public byte[] generate(Map<String, Object> d, String entityName) {
        Xlsx x = new Xlsx("PPMP");
        x.defaultFont(FONT, 9);
        x.page().landscape().paper(Xlsx.LEGAL).fitToWidth(1).fitToHeight(0).margins(0.5, 0.4, 0.5, 0.4);
        // A Code | B General Description | C Qty/Size | D Estimated Budget | E Mode | F..Q Jan..Dec | R Source of Funds | S Remarks
        x.width("A", 14);
        x.width("B", 40);
        x.width("C", 12);
        x.width("D", 15);
        x.width("E", 20);
        for (int i = 0; i < 12; i++) x.width(Xlsx.col(6 + i), 5);
        x.width("R", 20);
        x.width("S", 22);

        x.merge("A1:S1");
        x.set("A1", "PROJECT PROCUREMENT MANAGEMENT PLAN (PPMP)");
        x.style("A1").bold().size(14).center();
        x.merge("A2:S2");
        x.set("A2", "Fiscal Year " + d.get("fiscal_year"));
        x.style("A2").bold().size(11).center();
        x.merge("A3:S3");
        x.set("A3", or(entityName, ""));
        x.style("A3").size(10).center();

        List<Map<String, Object>> ppmps = (List<Map<String, Object>>) d.get("ppmps");
        x.merge("A5:I5");
        x.set("A5", "END-USER/UNIT: " + d.get("department"));
        x.style("A5").bold();
        x.merge("J5:S5");
        x.set("J5", "PPMP No.: " + (ppmps.isEmpty() ? "—" : String.join(", ", ppmps.stream().map(p -> String.valueOf(p.get("ppmp_number"))).toList())));
        x.style("J5").bold();
        x.merge("A6:S6");
        x.set("A6", "Projects, Programs and Activities (PAPs): " + (ppmps.isEmpty() ? "—" : String.join("; ", ppmps.stream().map(p -> String.valueOf(p.get("title"))).toList())));

        int h = 8;
        String[][] heads = {{"A", "CODE"}, {"B", "GENERAL DESCRIPTION"}, {"C", "QUANTITY / SIZE"}, {"D", "ESTIMATED BUDGET"},
                {"E", "MODE OF PROCUREMENT"}, {"R", "SOURCE OF FUNDS"}, {"S", "REMARKS"}};
        for (String[] c : heads) {
            x.merge(c[0] + h + ":" + c[0] + (h + 1));
            x.set(c[0] + h, c[1]);
        }
        x.merge("F" + h + ":Q" + h);
        x.set("F" + h, "SCHEDULE / MILESTONE OF ACTIVITIES");
        for (int i = 0; i < 12; i++) x.set(Xlsx.col(6 + i) + (h + 1), MONTH_LABELS[i]);
        x.style("A" + h + ":S" + (h + 1)).bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.height(h, 18);
        x.height(h + 1, 24);

        int r = h + 2;
        for (Map<String, Object> row : (List<Map<String, Object>>) d.get("rows")) {
            x.set("A" + r, row.get("code"));
            String desc = (String) row.get("general_description");
            String spec = (String) row.get("specifications");
            x.set("B" + r, spec == null || spec.isBlank() ? desc : desc + "\n" + spec);
            x.set("C" + r, row.get("quantity_size"));
            x.set("D" + r, row.get("estimated_budget"));
            x.set("E" + r, row.get("procurement_mode"));
            for (int i = 0; i < 12; i++) x.set(Xlsx.col(6 + i) + r, row.get(MONTHS[i]));
            x.set("R" + r, row.get("source_of_funds"));
            x.set("S" + r, row.get("remarks"));
            r++;
        }
        int first = h + 2, last = Math.max(first, r - 1);
        if (r == first) {
            x.merge("A" + r + ":S" + r);
            x.set("A" + r, "No PPMP lines.");
            x.style("A" + r).italic().center();
            r++;
            last = first;
        }
        x.style("A" + first + ":S" + last).border(Edge.ALL, Line.THIN).vcenter();
        x.style("B" + first + ":B" + last).wrap();
        x.style("S" + first + ":S" + last).wrap();
        x.style("C" + first + ":C" + last).center();
        x.style("F" + first + ":Q" + last).center().bold();
        x.style("D" + first + ":D" + r).numberFormat("#,##0.00");
        x.merge("A" + r + ":C" + r);
        x.set("A" + r, "TOTAL ESTIMATED BUDGET");
        x.set("D" + r, d.get("total_estimated_budget"));
        x.style("A" + r + ":S" + r).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + r).right();
        x.style("A" + h + ":S" + r).border(Edge.OUTLINE, Line.MEDIUM);

        int s = r + 3;
        x.set("A" + s, "Prepared by:");
        x.set("J" + s, "Approved by:");
        x.merge("A" + (s + 3) + ":D" + (s + 3));
        x.merge("J" + (s + 3) + ":Q" + (s + 3));
        x.set("A" + (s + 3), or((String) d.get("prepared_by"), "________________________________"));
        x.set("J" + (s + 3), or((String) d.get("approved_by"), "________________________________"));
        x.style("A" + (s + 3) + ":D" + (s + 3)).bold().center().border(Edge.BOTTOM, Line.THIN);
        x.style("J" + (s + 3) + ":Q" + (s + 3)).bold().center().border(Edge.BOTTOM, Line.THIN);
        x.merge("A" + (s + 4) + ":D" + (s + 4));
        x.merge("J" + (s + 4) + ":Q" + (s + 4));
        x.set("A" + (s + 4), "End-User / Head of Unit");
        x.set("J" + (s + 4), or((String) d.get("approved_by_designation"), "Head of Office"));
        x.style("A" + (s + 4) + ":Q" + (s + 4)).italic().center();
        x.merge("A" + (s + 5) + ":D" + (s + 5));
        x.merge("J" + (s + 5) + ":Q" + (s + 5));
        x.set("A" + (s + 5), "Date: ____________________");
        x.set("J" + (s + 5), "Date: ____________________");
        x.style("A" + (s + 5) + ":Q" + (s + 5)).center();
        x.set("A" + (s + 7), "Schedule: the plan records one quarter per line; the months of that quarter are marked X.");
        x.style("A" + (s + 7)).italic().size(8);
        return x.bytes();
    }

    static String or(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
