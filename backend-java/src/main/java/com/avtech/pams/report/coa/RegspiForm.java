package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registry of Semi-Expendable Property Issued (RegSPI) — COA Circular 2022-004, Annex A.4: every
 * ICS issue in the period (optionally one office), with the quantity issued, returned and still
 * held, and its amount at the issue's unit cost.
 */
@Component
public class RegspiForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public RegspiForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    /** The semi-expendable issues dated from..to, oldest first; unit cost from the request line, else the item's average. */
    static List<Map<String, Object>> issues(JdbcTemplate jdbc, LocalDate from, LocalDate to, Long departmentId) {
        List<Object> args = new ArrayList<>(List.of(from, to));
        String office = "";
        if (departmentId != null) {
            office = " and s.department_id = ?";
            args.add(departmentId);
        }
        return jdbc.queryForList("""
                select s.id, s.issued_date, s.ics_number, s.quantity, s.quantity_returned, s.status, s.remarks, s.custodian_name,
                       i.code, i.name, i.description, i.unit_of_measure, d.name as department_name, d.code as department_code, u.name as holder_name,
                       coalesce(ri.unit_cost, i.weighted_average_cost) as unit_cost
                from semi_expendable_issues s
                join items i on i.id = s.item_id
                left join departments d on d.id = s.department_id
                left join users u on u.id = s.issued_to
                left join request_items ri on ri.id = (select min(x.id) from request_items x where x.request_id = s.request_id and x.item_id = s.item_id)
                where s.issued_date >= ? and s.issued_date <= ?""" + office + " order by s.issued_date, s.ics_number, s.id", args.toArray());
    }

    static String officer(Map<String, Object> s) {
        String who = RpciForm.or((String) s.get("holder_name"), ItemMovements.str(s.get("custodian_name")));
        String dept = ItemMovements.str(s.get("department_name"));
        return dept.isEmpty() ? who : who.isEmpty() ? dept : dept + " / " + who;
    }

    public byte[] generate(LocalDate from, LocalDate to, Long departmentId, String officeName, String entityName, String fundCluster) {
        List<Map<String, Object>> rows = issues(jdbc, from, to, departmentId);

        Xlsx x = new Xlsx("RegSPI");
        x.defaultFont(FONT, 10);
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.4).right(0.4);

        // A Date | B Reference | C SE Property No. | D Item Description | E Est. Useful Life | F-G Issued | H-I Returned
        // | J-K Re-issued | L Disposed Qty | M Balance Qty | N Amount | O Remarks
        double[] widths = {10, 15, 13, 26, 9, 6, 18, 6, 14, 6, 14, 8, 8, 12, 14};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set("O1", "Annex A.4");
        x.style("O1").size(9).italic().right();
        x.merge("A3:O3");
        x.set("A3", "REGISTRY OF SEMI-EXPENDABLE PROPERTY ISSUED");
        x.style("A3").size(14).bold().center();
        x.merge("A4:O4");
        x.set("A4", "For the period " + PropertyCardForm.mdy(from) + " to " + PropertyCardForm.mdy(to)
                + (officeName != null ? " — " + officeName : ""));
        x.style("A4").center();

        x.set("A6", "Entity Name: " + RpciForm.or(entityName, "___________________________________"));
        x.set("L6", "Fund Cluster: " + RpciForm.or(fundCluster, "_________________"));
        x.style("A6:O6").bold();
        x.set("A7", "Semi-Expendable Property: All classes");
        x.set("L7", "Sheet No.: 1");

        x.set("A9", "Date");
        x.set("B9", "Reference");
        x.set("C9", "Semi-Expendable Property No.");
        x.set("D9", "Item Description");
        x.set("E9", "Estimated Useful Life");
        x.set("F9", "Issued");
        x.set("H9", "Returned");
        x.set("J9", "Re-issued");
        x.set("L9", "Disposed");
        x.set("M9", "Balance");
        x.set("N9", "Amount");
        x.set("O9", "Remarks");
        x.set("B10", "ICS/RRSP No.");
        for (String c : new String[]{"F", "H", "J"}) {
            x.set(c + "10", "Qty.");
            x.set(Xlsx.col(Xlsx.colIndex(c) + 1) + "10", "Office/Officer");
        }
        x.set("L10", "Qty.");
        x.set("M10", "Qty.");
        for (String m : new String[]{"A9:A10", "C9:C10", "D9:D10", "E9:E10", "F9:G9", "H9:I9", "J9:K9", "N9:N10", "O9:O10"}) x.merge(m);
        x.style("A9:O10").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        int row = 11;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> s : rows) {
            int qty = ((Number) s.get("quantity")).intValue();
            int back = s.get("quantity_returned") == null ? 0 : ((Number) s.get("quantity_returned")).intValue();
            BigDecimal amount = ItemMovements.money(s.get("unit_cost")).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            String who = officer(s);
            x.set("A" + row, PropertyCardForm.mdy(s.get("issued_date")));
            x.set("B" + row, ItemMovements.str(s.get("ics_number")));
            x.set("C" + row, ItemMovements.str(s.get("code")));
            String desc = ItemMovements.str(s.get("name"));
            String more = ItemMovements.str(s.get("description"));
            x.set("D" + row, more.isEmpty() ? desc : desc + " — " + more);
            x.set("F" + row, qty);
            x.set("G" + row, who);
            if (back > 0) {
                x.set("H" + row, back);
                x.set("I" + row, who);
            }
            x.set("M" + row, qty - back);
            x.set("N" + row, amount);
            x.set("O" + row, ItemMovements.str(s.get("remarks")));
            x.style("A" + row + ":O" + row).border(Edge.ALL, Line.THIN).vcenter().size(9);
            x.style("D" + row).wrap();
            x.style("G" + row).wrap();
            x.style("I" + row).wrap();
            x.style("A" + row).center();
            for (String c : new String[]{"F", "H", "J", "L", "M"}) x.style(c + row).center();
            x.style("N" + row).numberFormat("#,##0.00");
            row++;
        }
        for (int i = rows.size(); i < 15; i++) {
            x.style("A" + row + ":O" + row).border(Edge.ALL, Line.THIN);
            row++;
        }
        x.merge("A" + row + ":M" + row);
        x.set("A" + row, "TOTAL");
        x.set("N" + row, total);
        x.style("A" + row + ":O" + row).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + row).right();
        x.style("N" + row).numberFormat("#,##0.00");

        row += 3;
        x.set("A" + row, "Prepared by:");
        x.set("J" + row, "Certified Correct:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("property_officer"));
        Signatories.stamp(x, "J" + (row + 1), "J" + (row + 3), signatories.resolve("head_of_agency"));
        x.set("A" + (row + 4), "Property and/or Supply Custodian");
        x.set("J" + (row + 4), "Head of Agency / Authorized Representative");
        x.style("A" + (row + 4) + ":O" + (row + 4)).size(8).italic();
        return x.bytes();
    }
}
