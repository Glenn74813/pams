package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Annual Procurement Plan for Common-Use Supplies and Equipment (APP-CSE), the PS-DBM / GPPB form:
 * each item with its unit, quantity per quarter, total quantity, price catalogue and total amount.
 *
 * <p>The schema has no "supplies" category (lines are Goods, Infrastructure Projects or Consulting
 * Services), so the common-use lines are the Goods lines of the fiscal year's APP (or of its
 * approved PPMPs). Lines with the same description and unit are added up across end-user units; a
 * line's single Q1..Q4 schedule puts its whole quantity in that quarter; the price catalogue is the
 * estimated unit cost (the quantity-weighted average when units planned different prices).
 */
@Component
public class AppCseForm {

    private static final String FONT = "Arial";
    public static final String CATEGORY = "Goods";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public AppCseForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    private static final class Group {
        String description, unit, code;
        final long[] q = new long[4];
        long unscheduled, total;
        BigDecimal amount = BigDecimal.ZERO;
        final Set<BigDecimal> prices = new LinkedHashSet<>();
        final Set<String> units = new LinkedHashSet<>();
    }

    public Map<String, Object> data(int fiscalYear, String source, Long departmentId) {
        String src = source == null || source.isBlank() ? "app" : source.trim();
        if (!List.of("app", "ppmp").contains(src)) throw ApiException.validation("source", "Source is app or ppmp.");
        List<Object> args = new ArrayList<>();
        String sql;
        Map<String, Object> app = null;
        if ("app".equals(src)) {
            List<Map<String, Object>> apps = jdbc.queryForList(
                    "select id, app_number, title, status from apps where fiscal_year = ? and deleted_at is null order by id desc offset 0 rows fetch next 1 rows only", fiscalYear);
            app = apps.isEmpty() ? null : apps.get(0);
            sql = """
                    select ai.general_description as description, ai.unit, ai.quantity, ai.estimated_budget as amount, ai.schedule,
                           coalesce(pi.estimated_unit_cost, case when ai.quantity > 0 then round(ai.estimated_budget / ai.quantity, 2) end) as unit_cost,
                           pi.code, d.name as department
                    from app_items ai left join ppmp_items pi on pi.id = ai.ppmp_item_id left join departments d on d.id = ai.department_id
                    where ai.app_id = ? and ai.procurement_category = ?""";
            args.add(app == null ? -1L : ((Number) app.get("id")).longValue());
            args.add(CATEGORY);
            if (departmentId != null) {
                sql += " and ai.department_id = ?";
                args.add(departmentId);
            }
            sql += " order by ai.id";
        } else {
            sql = """
                    select pi.general_description as description, pi.unit, pi.quantity, pi.estimated_total as amount, pi.schedule,
                           pi.estimated_unit_cost as unit_cost, pi.code, d.name as department
                    from ppmp_items pi join ppmps p on p.id = pi.ppmp_id left join departments d on d.id = p.department_id
                    where p.fiscal_year = ? and p.deleted_at is null and p.status = 'approved' and pi.procurement_category = ?""";
            args.add(fiscalYear);
            args.add(CATEGORY);
            if (departmentId != null) {
                sql += " and p.department_id = ?";
                args.add(departmentId);
            }
            sql += " order by p.ppmp_number, pi.id";
        }
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Map<String, Object> l : jdbc.queryForList(sql, args.toArray())) {
            String desc = ((String) l.get("description")).trim();
            String unit = l.get("unit") == null ? "" : ((String) l.get("unit")).trim();
            String key = desc.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + "|" + unit.toLowerCase(Locale.ROOT);
            Group g = groups.computeIfAbsent(key, k -> {
                Group n = new Group();
                n.description = desc;
                n.unit = unit.isEmpty() ? null : unit;
                return n;
            });
            if (g.code == null) g.code = (String) l.get("code");
            long qty = ((Number) l.get("quantity")).longValue();
            int[] m = PpmpGppbForm.months((String) l.get("schedule"));
            if (m.length == 0) g.unscheduled += qty;
            else g.q[(m[0] - 1) / 3] += qty;
            g.total += qty;
            g.amount = g.amount.add((BigDecimal) l.get("amount"));
            if (l.get("unit_cost") != null) g.prices.add(((BigDecimal) l.get("unit_cost")).setScale(2, RoundingMode.HALF_UP));
            if (l.get("department") != null) g.units.add((String) l.get("department"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int no = 1;
        for (Group g : groups.values()) {
            Map<String, Object> r = new LinkedHashMap<>();
            BigDecimal price = g.prices.size() == 1 ? g.prices.iterator().next()
                    : g.total > 0 ? g.amount.divide(BigDecimal.valueOf(g.total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            r.put("no", no++);
            r.put("code", g.code);
            r.put("item", g.description);
            r.put("unit", g.unit);
            r.put("q1", g.q[0]);
            r.put("q2", g.q[1]);
            r.put("q3", g.q[2]);
            r.put("q4", g.q[3]);
            r.put("unscheduled", g.unscheduled);
            r.put("total_quantity", g.total);
            r.put("price_catalogue", price);
            r.put("total_amount", g.amount.setScale(2, RoundingMode.HALF_UP));
            r.put("end_users", String.join(", ", g.units));
            List<String> notes = new ArrayList<>();
            if (g.unscheduled > 0) notes.add(g.unscheduled + " not scheduled to a quarter");
            if (g.prices.size() > 1) notes.add("average of " + g.prices.size() + " planned prices");
            r.put("remarks", notes.isEmpty() ? null : String.join("; ", notes));
            rows.add(r);
            total = total.add(g.amount);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Annual Procurement Plan for Common-Use Supplies and Equipment (APP-CSE)");
        out.put("fiscal_year", fiscalYear);
        out.put("source", src);
        out.put("app_number", app == null ? null : app.get("app_number"));
        out.put("department_id", departmentId);
        out.put("department", departmentId == null ? null : jdbc.queryForList("select name from departments where id = ?", String.class, departmentId).stream().findFirst().orElse(null));
        out.put("category", CATEGORY);
        out.put("mapping", "Goods lines of the " + ("app".equals(src) ? "fiscal year's APP" : "fiscal year's approved PPMPs")
                + ", grouped by description and unit; each line's quarter receives its whole quantity; price catalogue = estimated unit cost.");
        out.put("total_amount", total.setScale(2, RoundingMode.HALF_UP));
        out.put("prepared_by", signatories.resolve("supply_officer").name());
        out.put("certified_by", signatories.resolve("accountant").name());
        out.put("approved_by", signatories.resolve("head_of_agency").name());
        out.put("rows", rows);
        return out;
    }

    @SuppressWarnings("unchecked")
    public byte[] generate(Map<String, Object> d, String entityName) {
        Xlsx x = new Xlsx("APP-CSE");
        x.defaultFont(FONT, 9);
        x.page().landscape().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.5, 0.4, 0.5, 0.4);
        // A No | B Code | C Item & Specifications | D Unit | E..H Q1..Q4 | I Total Qty | J Price Catalogue | K Total Amount | L Remarks
        String[] widths = {"A:6", "B:14", "C:42", "D:10", "E:9", "F:9", "G:9", "H:9", "I:11", "J:15", "K:16", "L:28"};
        for (String w : widths) x.width(w.split(":")[0], Double.parseDouble(w.split(":")[1]));
        x.merge("A1:L1");
        x.set("A1", "ANNUAL PROCUREMENT PLAN FOR COMMON-USE SUPPLIES AND EQUIPMENT (APP-CSE)");
        x.style("A1").bold().size(13).center();
        x.merge("A2:L2");
        x.set("A2", "Fiscal Year " + d.get("fiscal_year"));
        x.style("A2").bold().center();
        x.merge("A4:G4");
        x.set("A4", "Department / Agency: " + PpmpGppbForm.or(entityName, "______________________________"));
        x.style("A4").bold();
        x.merge("H4:L4");
        Object dept = d.get("department");
        x.set("H4", "End-User Unit: " + (dept == null ? "All units" : dept));
        x.style("H4").bold();
        x.merge("A5:L5");
        x.set("A5", "Source: " + ("app".equals(d.get("source")) ? "APP " + PpmpGppbForm.or((String) d.get("app_number"), "(none for this year)") : "approved PPMPs")
                + " — " + d.get("category") + " lines");

        int h = 7;
        String[][] vertical = {{"A", "No."}, {"B", "Code"}, {"C", "Item & Specifications"}, {"D", "Unit of Measure"}, {"I", "Total Quantity for the Year"},
                {"J", "Price Catalogue (as of the plan)"}, {"K", "Total Amount for the Year"}, {"L", "Remarks"}};
        for (String[] c : vertical) {
            x.merge(c[0] + h + ":" + c[0] + (h + 1));
            x.set(c[0] + h, c[1]);
        }
        x.merge("E" + h + ":H" + h);
        x.set("E" + h, "Quantity Requirement");
        x.set("E" + (h + 1), "Q1");
        x.set("F" + (h + 1), "Q2");
        x.set("G" + (h + 1), "Q3");
        x.set("H" + (h + 1), "Q4");
        x.style("A" + h + ":L" + (h + 1)).bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.height(h, 20);
        x.height(h + 1, 30);

        int r = h + 2;
        for (Map<String, Object> row : (List<Map<String, Object>>) d.get("rows")) {
            x.set("A" + r, row.get("no"));
            x.set("B" + r, row.get("code"));
            x.set("C" + r, row.get("item"));
            x.set("D" + r, row.get("unit"));
            x.set("E" + r, row.get("q1"));
            x.set("F" + r, row.get("q2"));
            x.set("G" + r, row.get("q3"));
            x.set("H" + r, row.get("q4"));
            x.set("I" + r, row.get("total_quantity"));
            x.set("J" + r, row.get("price_catalogue"));
            x.set("K" + r, row.get("total_amount"));
            x.set("L" + r, row.get("remarks"));
            r++;
        }
        int first = h + 2;
        if (r == first) {
            x.merge("A" + r + ":L" + r);
            x.set("A" + r, "No common-use supplies and equipment lines.");
            x.style("A" + r).italic().center();
            r++;
        }
        int last = r - 1;
        x.style("A" + first + ":L" + last).border(Edge.ALL, Line.THIN).vcenter();
        x.style("C" + first + ":C" + last).wrap();
        x.style("L" + first + ":L" + last).wrap();
        x.style("A" + first + ":A" + last).center();
        x.style("D" + first + ":I" + last).center();
        x.style("E" + first + ":I" + last).numberFormat("#,##0");
        x.style("J" + first + ":K" + r).numberFormat("#,##0.00");
        x.merge("A" + r + ":J" + r);
        x.set("A" + r, "TOTAL");
        x.set("K" + r, d.get("total_amount"));
        x.style("A" + r + ":L" + r).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + r).right();
        x.style("A" + h + ":L" + r).border(Edge.OUTLINE, Line.MEDIUM);

        int s = r + 3;
        AppGppbForm.sign(x, "A", "C", s, "Prepared by:", (String) d.get("prepared_by"), "Supply Officer");
        AppGppbForm.sign(x, "E", "I", s, "Certified funds available:", (String) d.get("certified_by"), "Accountant / Budget Officer");
        AppGppbForm.sign(x, "J", "L", s, "Approved by:", (String) d.get("approved_by"), "Head of Agency");
        x.set("A" + (s + 6), "Mapping: " + d.get("mapping"));
        x.style("A" + (s + 6)).italic().size(8);
        return x.bytes();
    }
}
