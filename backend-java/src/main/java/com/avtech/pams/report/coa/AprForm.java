package com.avtech.pams.report.coa;

import com.avtech.pams.common.Db;
import com.avtech.pams.procurement.ProcurementRules;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agency Procurement Report (APR), GPPB: for the fiscal year, per mode of procurement, the number
 * of procurement projects (awarded, unsuccessful, ongoing), the total approved budget for the
 * contract (ABC) and the total contract amount of the awards, with competitive bidding shown apart
 * from each alternative mode. Biddings are competitive bidding; RFQs count under their method, with
 * the winning quotation as the contract amount. A project's year is its purchase request's fiscal
 * year, or the year it was created.
 */
@Component
public class AprForm {

    private static final String FONT = "Arial";
    public static final String COMPETITIVE = "Competitive Bidding";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;
    private final Db db;

    public AprForm(JdbcTemplate jdbc, Signatories signatories, Db db) {
        this.jdbc = jdbc;
        this.db = db;
        this.signatories = signatories;
    }

    private static final class Tally {
        final String mode;
        long projects, awarded, unsuccessful, planned;
        BigDecimal abc = BigDecimal.ZERO, abcAwarded = BigDecimal.ZERO, contract = BigDecimal.ZERO, plannedBudget = BigDecimal.ZERO;

        Tally(String mode) {
            this.mode = mode;
        }

        void add(Tally t) {
            projects += t.projects;
            awarded += t.awarded;
            unsuccessful += t.unsuccessful;
            planned += t.planned;
            abc = abc.add(t.abc);
            abcAwarded = abcAwarded.add(t.abcAwarded);
            contract = contract.add(t.contract);
            plannedBudget = plannedBudget.add(t.plannedBudget);
        }
    }

    public Map<String, Object> data(int fiscalYear) {
        Map<String, Tally> by = new LinkedHashMap<>();
        for (String m : ProcurementRules.MODES) by.put(m, new Tally(m));
        jdbc.query("""
                select count(*), coalesce(sum(case when b.status = 'awarded' then 1 else 0 end), 0),
                       coalesce(sum(case when b.status in ('failed', 'cancelled') then 1 else 0 end), 0),
                       coalesce(sum(b.abc), 0), coalesce(sum(case when b.status = 'awarded' then b.abc end), 0),
                       coalesce(sum(case when b.status = 'awarded' then b.awarded_amount end), 0)
                from biddings b left join purchase_requests pr on pr.id = b.purchase_request_id
                where b.deleted_at is null and coalesce(pr.fiscal_year,""" + " " + db.year("b.created_at") + ") = ?", rs -> {
            fill(by.computeIfAbsent(COMPETITIVE, Tally::new), rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6));
        }, fiscalYear);
        jdbc.query("""
                select coalesce(nullif(trim(r.procurement_method), ''), 'Unspecified'), count(*), coalesce(sum(case when r.status = 'awarded' then 1 else 0 end), 0),
                       coalesce(sum(case when r.status = 'cancelled' then 1 else 0 end), 0), coalesce(sum(r.abc), 0),
                       coalesce(sum(case when r.status = 'awarded' then r.abc end), 0),
                       coalesce(sum(case when r.status = 'awarded' then w.total_amount end), 0)
                from rfqs r left join purchase_requests pr on pr.id = r.purchase_request_id
                left join (select q.rfq_id, q.total_amount, row_number() over (partition by q.rfq_id order by q.id) as rn
                           from rfq_quotations q where q.is_winner =""" + " " + db.t() + " " + """
                ) w on w.rfq_id = r.id and w.rn = 1
                where r.deleted_at is null and coalesce(pr.fiscal_year,""" + " " + db.year("r.created_at") + " " + """
                ) = ?
                group by coalesce(nullif(trim(r.procurement_method), ''), 'Unspecified')""", rs -> {
            fill(by.computeIfAbsent(rs.getString(1), Tally::new), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7));
        }, fiscalYear);
        jdbc.query("""
                select ai.procurement_mode, count(*), coalesce(sum(ai.estimated_budget), 0)
                from app_items ai join apps a on a.id = ai.app_id
                where a.deleted_at is null and a.fiscal_year = ? group by ai.procurement_mode""", rs -> {
            Tally t = by.computeIfAbsent(rs.getString(1), Tally::new);
            t.planned += rs.getLong(2);
            t.plannedBudget = t.plannedBudget.add(rs.getBigDecimal(3));
        }, fiscalYear);

        Tally alt = new Tally("Sub-total, Alternative Modes"), grand = new Tally("GRAND TOTAL");
        Tally cb = by.get(COMPETITIVE);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(cb, "competitive"));
        for (Tally t : by.values()) {
            if (t == cb) continue;
            rows.add(row(t, "alternative"));
            alt.add(t);
        }
        grand.add(cb);
        grand.add(alt);
        rows.add(row(alt, "subtotal"));
        rows.add(row(grand, "total"));
        for (Map<String, Object> r : rows) {
            BigDecimal c = (BigDecimal) r.get("contract_amount");
            r.put("share_of_contract_amount", grand.contract.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : c.multiply(BigDecimal.valueOf(100)).divide(grand.contract, 2, RoundingMode.HALF_UP));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Agency Procurement Report (APR)");
        out.put("fiscal_year", fiscalYear);
        out.put("competitive", row(cb, "competitive"));
        out.put("alternative", row(alt, "subtotal"));
        out.put("total", row(grand, "total"));
        out.put("prepared_by", signatories.resolve("bac_chairperson").name());
        out.put("approved_by", signatories.resolve("head_of_agency").name());
        out.put("rows", rows);
        return out;
    }

    private static void fill(Tally t, long projects, long awarded, long unsuccessful, BigDecimal abc, BigDecimal abcAwarded, BigDecimal contract) {
        t.projects += projects;
        t.awarded += awarded;
        t.unsuccessful += unsuccessful;
        t.abc = t.abc.add(abc);
        t.abcAwarded = t.abcAwarded.add(abcAwarded);
        t.contract = t.contract.add(contract);
    }

    private static Map<String, Object> row(Tally t, String section) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("section", section);
        m.put("mode", t.mode);
        m.put("planned_projects", t.planned);
        m.put("planned_budget", t.plannedBudget.setScale(2, RoundingMode.HALF_UP));
        m.put("projects", t.projects);
        m.put("awarded", t.awarded);
        m.put("unsuccessful", t.unsuccessful);
        m.put("ongoing", t.projects - t.awarded - t.unsuccessful);
        m.put("total_abc", t.abc.setScale(2, RoundingMode.HALF_UP));
        m.put("abc_of_awarded", t.abcAwarded.setScale(2, RoundingMode.HALF_UP));
        m.put("contract_amount", t.contract.setScale(2, RoundingMode.HALF_UP));
        m.put("savings", t.abcAwarded.subtract(t.contract).setScale(2, RoundingMode.HALF_UP));
        return m;
    }

    @SuppressWarnings("unchecked")
    public byte[] generate(Map<String, Object> d, String entityName) {
        Xlsx x = new Xlsx("APR");
        x.defaultFont(FONT, 9);
        x.page().landscape().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.5, 0.4, 0.5, 0.4);
        // A Mode | B Planned (APP) | C APP budget | D Projects | E Awarded | F Unsuccessful | G Ongoing | H Total ABC | I ABC of awarded | J Contract | K Savings | L %
        String[] widths = {"A:32", "B:10", "C:16", "D:10", "E:10", "F:12", "G:10", "H:16", "I:16", "J:16", "K:16", "L:10"};
        for (String w : widths) x.width(w.split(":")[0], Double.parseDouble(w.split(":")[1]));
        x.merge("A1:L1");
        x.set("A1", "AGENCY PROCUREMENT REPORT");
        x.style("A1").bold().size(14).center();
        x.merge("A2:L2");
        x.set("A2", "For Fiscal Year " + d.get("fiscal_year"));
        x.style("A2").bold().center();
        x.merge("A4:L4");
        x.set("A4", "Name of Agency / Procuring Entity: " + PpmpGppbForm.or(entityName, "______________________________"));
        x.style("A4").bold();

        int h = 6;
        x.merge("A" + h + ":A" + (h + 1));
        x.set("A" + h, "Mode of Procurement");
        x.merge("B" + h + ":C" + h);
        x.set("B" + h, "Per APP");
        x.set("B" + (h + 1), "No. of Projects");
        x.set("C" + (h + 1), "Estimated Budget");
        x.merge("D" + h + ":G" + h);
        x.set("D" + h, "Number of Procurement Projects");
        x.set("D" + (h + 1), "Total");
        x.set("E" + (h + 1), "Awarded");
        x.set("F" + (h + 1), "Failed / Cancelled");
        x.set("G" + (h + 1), "Ongoing");
        x.merge("H" + h + ":I" + h);
        x.set("H" + h, "Approved Budget for the Contract (ABC)");
        x.set("H" + (h + 1), "All Projects");
        x.set("I" + (h + 1), "Awarded Projects");
        x.merge("J" + h + ":J" + (h + 1));
        x.set("J" + h, "Total Contract Amount");
        x.merge("K" + h + ":K" + (h + 1));
        x.set("K" + h, "Savings (ABC less Contract Amount)");
        x.merge("L" + h + ":L" + (h + 1));
        x.set("L" + h, "% of Total Contract Amount");
        x.style("A" + h + ":L" + (h + 1)).bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.height(h + 1, 30);

        int r = h + 2;
        boolean altHeader = false;
        for (Map<String, Object> row : (List<Map<String, Object>>) d.get("rows")) {
            String section = (String) row.get("section");
            if ("alternative".equals(section) && !altHeader) {
                x.merge("A" + r + ":L" + r);
                x.set("A" + r, "ALTERNATIVE MODES OF PROCUREMENT");
                x.style("A" + r + ":L" + r).bold().border(Edge.ALL, Line.THIN);
                altHeader = true;
                r++;
            }
            x.set("A" + r, "alternative".equals(section) ? "   " + row.get("mode") : "competitive".equals(section) ? "COMPETITIVE BIDDING" : row.get("mode"));
            x.set("B" + r, row.get("planned_projects"));
            x.set("C" + r, row.get("planned_budget"));
            x.set("D" + r, row.get("projects"));
            x.set("E" + r, row.get("awarded"));
            x.set("F" + r, row.get("unsuccessful"));
            x.set("G" + r, row.get("ongoing"));
            x.set("H" + r, row.get("total_abc"));
            x.set("I" + r, row.get("abc_of_awarded"));
            x.set("J" + r, row.get("contract_amount"));
            x.set("K" + r, row.get("savings"));
            x.set("L" + r, row.get("share_of_contract_amount"));
            x.style("A" + r + ":L" + r).border(Edge.ALL, Line.THIN);
            if (!"alternative".equals(section)) x.style("A" + r + ":L" + r).bold();
            r++;
        }
        x.style("B" + (h + 2) + ":B" + (r - 1)).center().numberFormat("#,##0");
        x.style("D" + (h + 2) + ":G" + (r - 1)).center().numberFormat("#,##0");
        x.style("C" + (h + 2) + ":C" + (r - 1)).numberFormat("#,##0.00");
        x.style("H" + (h + 2) + ":K" + (r - 1)).numberFormat("#,##0.00");
        x.style("L" + (h + 2) + ":L" + (r - 1)).numberFormat("0.00\"%\"");
        x.style("A" + h + ":L" + (r - 1)).border(Edge.OUTLINE, Line.MEDIUM);

        int s = r + 2;
        AppGppbForm.sign(x, "A", "A", s, "Prepared by:", (String) d.get("prepared_by"), "BAC Chairperson");
        AppGppbForm.sign(x, "H", "K", s, "Approved by:", (String) d.get("approved_by"), "Head of Procuring Entity");
        x.set("A" + (s + 6), "Competitive bidding = biddings; alternative modes = RFQs by procurement method (contract amount = winning quotation). "
                + "A project's fiscal year is its purchase request's.");
        x.style("A" + (s + 6)).italic().size(8);
        return x.bytes();
    }
}
