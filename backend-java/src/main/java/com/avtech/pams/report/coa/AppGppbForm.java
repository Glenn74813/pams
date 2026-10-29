package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.setting.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Annual Procurement Plan (APP) in the GPPB-prescribed format: every line of the fiscal year's
 * consolidated APP with its PMO/end-user, mode, the month of each procurement activity, source of
 * funds and the estimated budget split into MOOE and Capital Outlay.
 *
 * <p>Activity months are the actual dates when a bidding or RFQ has been run for the line (through
 * a purchase request drawn on it); otherwise they are planned from the line's quarter: posting and
 * opening in the quarter's first month, notice of award and contract signing in the next.
 */
@Component
public class AppGppbForm {

    private static final String FONT = "Arial";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    /** Alternative modes with no posting of an invitation or request for quotation. */
    private static final Set<String> NO_POSTING = Set.of("Direct Contracting", "Direct Acquisition", "Repeat Order");

    private final JdbcTemplate jdbc;
    private final Signatories signatories;
    private final SettingService settings;

    public AppGppbForm(JdbcTemplate jdbc, Signatories signatories, SettingService settings) {
        this.jdbc = jdbc;
        this.signatories = signatories;
        this.settings = settings;
    }

    /**
     * MOOE or Capital Outlay for a line: the UACS object code decides when there is one (506… and
     * asset accounts 1… are CO, other 5… codes MOOE); otherwise infrastructure is CO, and goods are CO
     * when their unit cost reaches the capitalization threshold.
     */
    static boolean capital(String code, String category, BigDecimal unitCost, BigDecimal threshold) {
        String c = code == null ? "" : code.replaceAll("[^0-9]", "");
        if (c.startsWith("506") || c.startsWith("1")) return true;
        if (c.startsWith("5")) return false;
        if ("Infrastructure Projects".equals(category)) return true;
        return "Goods".equals(category) && unitCost != null && threshold != null && unitCost.compareTo(threshold) >= 0;
    }

    BigDecimal threshold() {
        try {
            return settings.capitalizationThreshold();
        } catch (RuntimeException e) {
            return new BigDecimal("50000");
        }
    }

    public Map<String, Object> data(int fiscalYear) {
        List<Map<String, Object>> apps = jdbc.queryForList("""
                select a.id, a.app_number, a.title, a.status, a.total_budget, a.approved_at, a.prepared_by, a.approved_by
                from apps a where a.fiscal_year = ? and a.deleted_at is null order by a.id desc offset 0 rows fetch next 1 rows only""", fiscalYear);
        Map<String, Object> app = apps.isEmpty() ? null : apps.get(0);
        BigDecimal threshold = threshold();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, mooe = BigDecimal.ZERO, co = BigDecimal.ZERO;
        if (app != null) {
            long appId = ((Number) app.get("id")).longValue();
            List<Map<String, Object>> lines = jdbc.queryForList("""
                    select ai.id, ai.general_description, ai.specifications, ai.unit, ai.quantity, ai.estimated_budget, ai.procurement_mode,
                           ai.procurement_category, ai.schedule, ai.fund_source, ai.remarks, ai.status, d.name as department, d.code as department_code,
                           pi.code, pi.estimated_unit_cost, p.ppmp_number,
                           pc.kind, pc.ref, pc.posted, pc.opened, pc.noa, pc.signed
                    from app_items ai
                    left join departments d on d.id = ai.department_id
                    left join ppmp_items pi on pi.id = ai.ppmp_item_id
                    left join ppmps p on p.id = ai.ppmp_id
                    left join (
                        select u.app_item_id, u.kind, u.ref, u.posted, u.opened, u.noa, u.signed,
                               row_number() over (partition by u.app_item_id
                                                  order by case when u.noa is null then 1 else 0 end, u.noa,
                                                           case when u.posted is null then 1 else 0 end, u.posted, u.kind, u.ref) as rn
                        from (
                            select l.app_item_id, 'bidding' as kind, b.bidding_number as ref, b.itb_posted_at as posted, cast(b.bid_opening_date as date) as opened,
                                   b.noa_date as noa,
                                   (select min(c.signed_date) from contracts c where c.bidding_id = b.id and c.deleted_at is null) as signed
                            from biddings b
                            join (select distinct pri.app_item_id, pri.purchase_request_id from purchase_request_items pri
                                  join purchase_requests pr on pr.id = pri.purchase_request_id
                                  join app_items x on x.id = pri.app_item_id
                                  where pr.deleted_at is null and x.app_id = ?) l on l.purchase_request_id = b.purchase_request_id
                            where b.deleted_at is null and b.status not in ('failed', 'cancelled')
                            union all
                            select l.app_item_id, 'rfq', r.rfq_number, case when r.status <> 'draft' then cast(r.created_at as date) end, cast(r.deadline as date),
                                   r.noa_date,
                                   (select min(c.signed_date) from contracts c where c.rfq_id = r.id and c.deleted_at is null)
                            from rfqs r
                            join (select distinct pri.app_item_id, pri.purchase_request_id from purchase_request_items pri
                                  join purchase_requests pr on pr.id = pri.purchase_request_id
                                  join app_items x on x.id = pri.app_item_id
                                  where pr.deleted_at is null and x.app_id = ?) l on l.purchase_request_id = r.purchase_request_id
                            where r.deleted_at is null and r.status <> 'cancelled'
                        ) u
                    ) pc on pc.app_item_id = ai.id and pc.rn = 1
                    where ai.app_id = ?
                    order by case when d.name is null then 1 else 0 end, d.name, ai.id""", appId, appId, appId);
            for (Map<String, Object> l : lines) {
                Map<String, Object> r = new LinkedHashMap<>();
                BigDecimal budget = (BigDecimal) l.get("estimated_budget");
                int qty = ((Number) l.get("quantity")).intValue();
                BigDecimal unitCost = l.get("estimated_unit_cost") != null ? (BigDecimal) l.get("estimated_unit_cost")
                        : qty > 0 ? budget.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP) : budget;
                String mode = (String) l.get("procurement_mode");
                boolean isCo = capital((String) l.get("code"), (String) l.get("procurement_category"), unitCost, threshold);
                String[] planned = planned((String) l.get("schedule"), mode, fiscalYear);
                String[] actual = {month(l.get("posted")), month(l.get("opened")), month(l.get("noa")), month(l.get("signed"))};
                int actuals = 0;
                String[] sched = new String[4];
                for (int i = 0; i < 4; i++) {
                    if (actual[i] != null) actuals++;
                    sched[i] = actual[i] != null ? actual[i] : planned[i];
                }
                r.put("app_item_id", l.get("id"));
                r.put("code", l.get("code"));
                r.put("procurement_project", l.get("general_description"));
                r.put("specifications", l.get("specifications"));
                r.put("quantity", qty);
                r.put("unit", l.get("unit"));
                r.put("pmo_end_user", l.get("department") == null ? "—" : l.get("department"));
                r.put("procurement_mode", mode);
                r.put("procurement_category", l.get("procurement_category"));
                r.put("schedule_quarter", l.get("schedule"));
                r.put("advertisement_posting", sched[0]);
                r.put("submission_opening", sched[1]);
                r.put("notice_of_award", sched[2]);
                r.put("contract_signing", sched[3]);
                r.put("schedule_basis", actuals == 0 ? (l.get("schedule") == null ? "unscheduled" : "planned") : actuals == 4 ? "actual" : "partly actual");
                r.put("procurement_reference", l.get("ref"));
                r.put("source_of_funds", l.get("fund_source"));
                r.put("estimated_budget_total", budget);
                r.put("estimated_budget_mooe", isCo ? BigDecimal.ZERO.setScale(2) : budget);
                r.put("estimated_budget_co", isCo ? budget : BigDecimal.ZERO.setScale(2));
                r.put("remarks", l.get("remarks"));
                r.put("status", l.get("status"));
                rows.add(r);
                total = total.add(budget);
                if (isCo) co = co.add(budget);
                else mooe = mooe.add(budget);
            }
        }
        Signatories.Signatory chair = signatories.resolve("bac_chairperson");
        Signatories.Signatory head = signatories.resolve("head_of_agency");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Annual Procurement Plan (APP)");
        out.put("fiscal_year", fiscalYear);
        if (app == null) {
            out.put("app", null);
        } else {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", app.get("id"));
            a.put("app_number", app.get("app_number"));
            a.put("title", app.get("title"));
            a.put("status", app.get("status"));
            a.put("approved_at", app.get("approved_at") == null ? null : app.get("approved_at").toString().substring(0, 10));
            out.put("app", a);
        }
        out.put("capitalization_threshold", threshold);
        out.put("total_budget", total);
        out.put("total_mooe", mooe);
        out.put("total_co", co);
        out.put("prepared_by", app == null ? null : userName(app.get("prepared_by")));
        out.put("recommended_by", chair.name());
        out.put("recommended_by_designation", chair.designation());
        String approver = head.name() != null ? head.name() : app == null ? null : userName(app.get("approved_by"));
        out.put("approved_by", approver);
        out.put("approved_by_designation", "Head of Procuring Entity");
        out.put("rows", rows);
        return out;
    }

    private String userName(Object id) {
        if (id == null) return null;
        List<String> n = jdbc.queryForList("select name from users where id = ?", String.class, ((Number) id).longValue());
        return n.isEmpty() ? null : n.get(0);
    }

    /** Posting, opening, NOA and contract-signing months planned from a Q1..Q4 schedule. */
    static String[] planned(String quarter, String mode, int year) {
        int[] m = PpmpGppbForm.months(quarter);
        if (m.length == 0) return new String[4];
        int start = m[0], next = Math.min(12, start + 1);
        String s = LocalDate.of(year, start, 1).format(MONTH), n = LocalDate.of(year, next, 1).format(MONTH);
        return new String[]{NO_POSTING.contains(mode) ? "N/A" : s, s, n, n};
    }

    private static String month(Object v) {
        if (v instanceof Date d) return d.toLocalDate().format(MONTH);
        if (v instanceof LocalDate d) return d.format(MONTH);
        return null;
    }

    @SuppressWarnings("unchecked")
    public byte[] generate(Map<String, Object> d, String entityName) {
        Xlsx x = new Xlsx("APP");
        x.defaultFont(FONT, 9);
        x.page().landscape().paper(Xlsx.LEGAL).fitToWidth(1).fitToHeight(0).margins(0.5, 0.4, 0.5, 0.4);
        // A Code | B Project | C PMO/End-User | D Mode | E..H activities | I Source of Funds | J..L Budget | M Remarks
        String[] widths = {"A:14", "B:38", "C:20", "D:20", "E:12", "F:12", "G:12", "H:12", "I:20", "J:15", "K:15", "L:15", "M:24"};
        for (String w : widths) x.width(w.split(":")[0], Double.parseDouble(w.split(":")[1]));

        Map<String, Object> app = (Map<String, Object>) d.get("app");
        x.merge("A1:M1");
        x.set("A1", PpmpGppbForm.or(entityName, ""));
        x.style("A1").bold().size(11).center();
        x.merge("A2:M2");
        x.set("A2", "ANNUAL PROCUREMENT PLAN FOR FY " + d.get("fiscal_year"));
        x.style("A2").bold().size(14).center();
        x.merge("A3:M3");
        x.set("A3", app == null ? "No APP has been consolidated for this fiscal year." : app.get("app_number") + " — " + app.get("title") + " (" + app.get("status") + ")");
        x.style("A3").center();

        int h = 5;
        String[][] vertical = {{"A", "Code (PAP)"}, {"B", "Procurement Program / Project"}, {"C", "PMO / End-User"}, {"D", "Mode of Procurement"},
                {"I", "Source of Funds"}, {"M", "Remarks (brief description of Program / Activity / Project)"}};
        for (String[] c : vertical) {
            x.merge(c[0] + h + ":" + c[0] + (h + 1));
            x.set(c[0] + h, c[1]);
        }
        x.merge("E" + h + ":H" + h);
        x.set("E" + h, "Schedule for Each Procurement Activity");
        x.set("E" + (h + 1), "Advertisement / Posting of IB/REI");
        x.set("F" + (h + 1), "Submission / Opening of Bids");
        x.set("G" + (h + 1), "Notice of Award");
        x.set("H" + (h + 1), "Contract Signing");
        x.merge("J" + h + ":L" + h);
        x.set("J" + h, "Estimated Budget (PhP)");
        x.set("J" + (h + 1), "Total");
        x.set("K" + (h + 1), "MOOE");
        x.set("L" + (h + 1), "CO");
        x.style("A" + h + ":M" + (h + 1)).bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.height(h + 1, 40);

        int r = h + 2;
        for (Map<String, Object> row : (List<Map<String, Object>>) d.get("rows")) {
            x.set("A" + r, row.get("code"));
            x.set("B" + r, row.get("procurement_project"));
            x.set("C" + r, row.get("pmo_end_user"));
            x.set("D" + r, row.get("procurement_mode"));
            x.set("E" + r, row.get("advertisement_posting"));
            x.set("F" + r, row.get("submission_opening"));
            x.set("G" + r, row.get("notice_of_award"));
            x.set("H" + r, row.get("contract_signing"));
            x.set("I" + r, row.get("source_of_funds"));
            x.set("J" + r, row.get("estimated_budget_total"));
            x.set("K" + r, row.get("estimated_budget_mooe"));
            x.set("L" + r, row.get("estimated_budget_co"));
            String remarks = (String) row.get("remarks");
            Object ref = row.get("procurement_reference");
            String basis = (String) row.get("schedule_basis");
            String note = ref != null ? "Ref: " + ref + " (" + basis + " dates)" : "planned".equals(basis) ? "Planned " + row.get("schedule_quarter") : null;
            x.set("M" + r, remarks == null ? note : note == null ? remarks : remarks + "; " + note);
            r++;
        }
        int first = h + 2;
        if (r == first) {
            x.merge("A" + r + ":M" + r);
            x.set("A" + r, "No APP lines.");
            x.style("A" + r).italic().center();
            r++;
        }
        int last = r - 1;
        x.style("A" + first + ":M" + last).border(Edge.ALL, Line.THIN).vcenter().wrap();
        x.style("E" + first + ":H" + last).center();
        x.style("J" + first + ":L" + r).numberFormat("#,##0.00");
        x.merge("A" + r + ":I" + r);
        x.set("A" + r, "TOTAL");
        x.set("J" + r, d.get("total_budget"));
        x.set("K" + r, d.get("total_mooe"));
        x.set("L" + r, d.get("total_co"));
        x.style("A" + r + ":M" + r).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + r).right();
        x.style("A" + h + ":M" + r).border(Edge.OUTLINE, Line.MEDIUM);

        int s = r + 3;
        sign(x, "A", "C", s, "Prepared by:", (String) d.get("prepared_by"), "BAC Secretariat");
        sign(x, "E", "H", s, "Recommended by:", (String) d.get("recommended_by"), (String) d.get("recommended_by_designation"));
        sign(x, "J", "M", s, "Approved by:", (String) d.get("approved_by"), (String) d.get("approved_by_designation"));
        x.set("A" + (s + 6), "Schedule months are actual where a bidding or RFQ has been held for the line, otherwise planned from the line's quarter. "
                + "MOOE/CO follows the UACS code, else Capital Outlay for infrastructure and goods at or above the capitalization threshold.");
        x.style("A" + (s + 6)).italic().size(8);
        return x.bytes();
    }

    static void sign(Xlsx x, String from, String to, int row, String caption, String name, String designation) {
        x.set(from + row, caption);
        x.merge(from + (row + 3) + ":" + to + (row + 3));
        x.set(from + (row + 3), PpmpGppbForm.or(name, "________________________________"));
        x.style(from + (row + 3) + ":" + to + (row + 3)).bold().center().border(Edge.BOTTOM, Line.THIN);
        x.merge(from + (row + 4) + ":" + to + (row + 4));
        x.set(from + (row + 4), PpmpGppbForm.or(designation, ""));
        x.style(from + (row + 4)).italic().center();
    }
}
