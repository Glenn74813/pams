package com.avtech.pams.report.coa;

import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Report of Semi-Expendable Property Issued (RSPI) — COA Circular 2022-004, Annex A.7: the ICS
 * issues of the period by ICS number, prepared by the Property/Supply Custodian and posted by
 * Accounting to the Semi-expendable Property Ledger Cards.
 */
@Component
public class RspiForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public RspiForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public byte[] generate(LocalDate from, LocalDate to, String serialNo, String entityName, String fundCluster) {
        List<Map<String, Object>> rows = RegspiForm.issues(jdbc, from, to, null);

        Xlsx x = new Xlsx("RSPI");
        x.defaultFont(FONT, 10);
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        // A ICS No. | B Responsibility Center Code | C SE Property No. | D Item Description | E Unit | F Qty Issued | G Unit Cost | H Amount
        double[] widths = {16, 13, 14, 32, 8, 9, 12, 13};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set("H1", "Annex A.7");
        x.style("H1").size(9).italic().right();
        x.merge("A3:H3");
        x.set("A3", "REPORT OF SEMI-EXPENDABLE PROPERTY ISSUED");
        x.style("A3").size(14).bold().center();
        x.merge("A4:H4");
        x.set("A4", "For the period " + PropertyCardForm.mdy(from) + " to " + PropertyCardForm.mdy(to));
        x.style("A4").center();

        x.set("A6", "Entity Name: " + RpciForm.or(entityName, "___________________________________"));
        x.set("F6", "Serial No.: " + RpciForm.or(serialNo, "____________"));
        x.set("A7", "Fund Cluster: " + RpciForm.or(fundCluster, "_________________"));
        x.set("F7", "Date: " + PropertyCardForm.mdy(LocalDate.now(Times.ZONE)));
        x.style("A6:H7").bold();

        x.merge("A9:F9");
        x.set("A9", "To be filled up by the Property and/or Supply Division/Unit");
        x.merge("G9:H9");
        x.set("G9", "To be filled up by the Accounting Division/Unit");
        x.set("A10", "ICS No.");
        x.set("B10", "Responsibility Center Code");
        x.set("C10", "Semi-Expendable Property No.");
        x.set("D10", "Item Description");
        x.set("E10", "Unit");
        x.set("F10", "Quantity Issued");
        x.set("G10", "Unit Cost");
        x.set("H10", "Amount");
        x.style("A9:H10").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.style("A9:H9").italic();

        int row = 11;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> s : rows) {
            int qty = ((Number) s.get("quantity")).intValue();
            BigDecimal unit = ItemMovements.money(s.get("unit_cost")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = unit.multiply(BigDecimal.valueOf(qty));
            total = total.add(amount);
            x.set("A" + row, ItemMovements.str(s.get("ics_number")));
            x.set("B" + row, RpciForm.or((String) s.get("department_code"), ItemMovements.str(s.get("department_name"))));
            x.set("C" + row, ItemMovements.str(s.get("code")));
            x.set("D" + row, ItemMovements.str(s.get("name")));
            x.set("E" + row, ItemMovements.str(s.get("unit_of_measure")));
            x.set("F" + row, qty);
            x.set("G" + row, unit);
            x.set("H" + row, amount);
            x.style("A" + row + ":H" + row).border(Edge.ALL, Line.THIN).vcenter().size(9);
            x.style("D" + row).wrap();
            x.style("E" + row + ":F" + row).center();
            x.style("G" + row + ":H" + row).numberFormat("#,##0.00");
            row++;
        }
        for (int i = rows.size(); i < 15; i++) {
            x.style("A" + row + ":H" + row).border(Edge.ALL, Line.THIN);
            row++;
        }
        x.merge("A" + row + ":G" + row);
        x.set("A" + row, "TOTAL");
        x.set("H" + row, total);
        x.style("A" + row + ":H" + row).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + row).right();
        x.style("H" + row).numberFormat("#,##0.00");

        row += 2;
        x.merge("A" + row + ":D" + row);
        x.set("A" + row, "I hereby certify to the correctness of the above information.");
        x.set("E" + row, "Posted by:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("property_officer"));
        Signatories.stamp(x, "E" + (row + 1), "E" + (row + 3), signatories.resolve("accountant"));
        x.set("A" + (row + 4), "Signature over Printed Name of Property and/or Supply Custodian");
        x.set("E" + (row + 4), "Signature over Printed Name of Designated Accounting Staff");
        x.style("A" + (row + 4) + ":H" + (row + 4)).size(8).italic();
        x.set("E" + (row + 5), "Date: ________________");
        return x.bytes();
    }
}
