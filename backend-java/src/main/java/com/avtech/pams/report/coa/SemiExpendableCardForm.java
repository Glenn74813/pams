package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Semi-expendable Property Card (Annex A.1, kept by the Property Division) and Semi-expendable
 * Property Ledger Card (Annex A.2, kept by Accounting) — COA Circular 2022-004 — for one
 * semi-expendable item: receipts, issues under ICS to an office/officer, and the balance.
 */
@Component
public class SemiExpendableCardForm {

    private static final String FONT = "Times New Roman";

    private final ItemMovements movements;
    private final Signatories signatories;

    public SemiExpendableCardForm(ItemMovements movements, Signatories signatories) {
        this.movements = movements;
        this.signatories = signatories;
    }

    /** {@code ledger}: the Ledger Card (amounts on issues and balance) instead of the Property Card. */
    public byte[] generate(long itemId, boolean ledger, String entityName, String fundCluster) {
        ItemMovements.Card card = movements.of(itemId);
        Map<String, Object> item = card.item();
        if (!Boolean.TRUE.equals(item.get("is_semi_expandable")) && !Integer.valueOf(1).equals(item.get("is_semi_expandable"))) {
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT, "\"" + item.get("name") + "\" is not semi-expendable property; use the Stock Card or Supplies Ledger Card.");
        }

        // Property Card:  A Date | B Reference | C-E Receipt (Qty, Unit Cost, Total) | F-H Issue (Item No., Qty, Office/Officer) | I Balance Qty | J Amount | K Remarks
        // Ledger Card:    A Date | B Reference | C-E Receipt | F-I Issue (Item No., Qty, Office/Officer, Amount) | J-K Balance (Qty, Amount) | L Remarks
        String last = ledger ? "L" : "K";
        double[] widths = ledger ? new double[]{11, 16, 7, 11, 12, 15, 7, 20, 12, 7, 13, 16} : new double[]{11, 16, 7, 11, 12, 15, 7, 22, 8, 13, 18};

        Xlsx x = new Xlsx(ledger ? "SPLC" : "SPC");
        x.defaultFont(FONT, 10);
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set(last + "1", ledger ? "Annex A.2" : "Annex A.1");
        x.style(last + "1").size(9).italic().right();

        x.merge("A3:" + last + "3");
        x.set("A3", ledger ? "SEMI-EXPENDABLE PROPERTY LEDGER CARD" : "SEMI-EXPENDABLE PROPERTY CARD");
        x.style("A3").size(14).bold().center();

        String right = ledger ? "I" : "H";
        x.set("A5", "Entity Name: " + RpciForm.or(entityName, "___________________________________"));
        x.set(right + "5", "Fund Cluster: " + RpciForm.or(fundCluster, "_________________"));
        x.style("A5:" + last + "5").bold();
        x.set("A7", "Semi-expendable Property: " + ItemMovements.str(item.get("name")));
        x.set(right + "7", "Semi-expendable Property Number: " + ItemMovements.str(item.get("code")));
        x.set("A8", "Description: " + ItemMovements.str(item.get("description")));
        x.set(right + "8", "Unit of Measurement: " + ItemMovements.str(item.get("unit_of_measure")));
        if (ledger) x.set(right + "9", "Estimated Useful Life: ____________");

        x.set("A11", "Date");
        x.set("B11", "Reference");
        x.set("C11", "Receipt");
        x.set("F11", "Issue/Transfer/Disposal");
        x.set("C12", "Qty.");
        x.set("D12", "Unit Cost");
        x.set("E12", "Total Cost");
        x.set("F12", "Item No.");
        x.set("G12", "Qty.");
        x.set("H12", "Office/Officer");
        x.merge("A11:A12");
        x.merge("B11:B12");
        x.merge("C11:E11");
        if (ledger) {
            x.set("I12", "Amount");
            x.set("J11", "Balance");
            x.set("J12", "Qty.");
            x.set("K12", "Amount");
            x.set("L11", "Remarks");
            x.merge("F11:I11");
            x.merge("J11:K11");
            x.merge("L11:L12");
        } else {
            x.set("I11", "Balance Qty.");
            x.set("J11", "Amount");
            x.set("K11", "Remarks");
            x.merge("F11:H11");
            x.merge("I11:I12");
            x.merge("J11:J12");
            x.merge("K11:K12");
        }
        x.style("A11:" + last + "12").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        int row = 13;
        x.set("B" + row, "Balance forwarded");
        BigDecimal openAmount = card.openingCost().multiply(BigDecimal.valueOf(Math.max(0, card.openingQty())));
        x.set((ledger ? "J" : "I") + row, card.openingQty());
        x.set((ledger ? "K" : "J") + row, openAmount);
        styleRow(x, row, ledger, last);
        row++;
        for (ItemMovements.Line l : card.lines()) {
            x.set("A" + row, PropertyCardForm.mdy(l.date()));
            x.set("B" + row, l.reference());
            if (l.qty() > 0) {
                x.set("C" + row, l.qty());
                x.set("D" + row, l.unitCost());
                x.set("E" + row, l.amount());
            } else {
                x.set("F" + row, l.itemNo());
                x.set("G" + row, -l.qty());
                String who = l.officer().isEmpty() ? l.office() : l.office().isEmpty() ? l.officer() : l.office() + " / " + l.officer();
                x.set("H" + row, who);
                if (ledger) x.set("I" + row, l.amount());
            }
            x.set((ledger ? "J" : "I") + row, l.balance());
            x.set((ledger ? "K" : "J") + row, l.balanceAmount());
            x.set(last + row, l.remarks());
            styleRow(x, row, ledger, last);
            row++;
        }
        for (int i = card.lines().size(); i < 15; i++) {
            x.style("A" + row + ":" + last + row).border(Edge.ALL, Line.THIN);
            row++;
        }

        row += 2;
        x.set("A" + row, "Prepared by:");
        x.set(right + row, "Certified Correct:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve(ledger ? "accountant" : "property_officer"));
        Signatories.stamp(x, right + (row + 1), right + (row + 3), signatories.resolve("head_of_agency"));
        x.set("A" + (row + 4), ledger ? "Accountant / Bookkeeper" : "Property/Supply Officer");
        x.set(right + (row + 4), "Head of Agency / Authorized Representative");
        x.style("A" + (row + 4) + ":" + last + (row + 4)).size(8).italic();
        return x.bytes();
    }

    private static void styleRow(Xlsx x, int row, boolean ledger, String last) {
        x.style("A" + row + ":" + last + row).border(Edge.ALL, Line.THIN).vcenter();
        x.style("A" + row).size(9).center();
        x.style("C" + row).center();
        x.style("G" + row).center();
        x.style("H" + row).wrap();
        x.style(last + row).size(8).wrap();
        for (String c : ledger ? new String[]{"D", "E", "I", "K"} : new String[]{"D", "E", "J"}) x.style(c + row).numberFormat("#,##0.00");
        x.style((ledger ? "J" : "I") + row).center();
    }
}
