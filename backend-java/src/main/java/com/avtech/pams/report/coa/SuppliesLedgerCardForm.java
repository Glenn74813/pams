package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Supplies Ledger Card (SLC) — COA GAM Appendix 57: one item's receipts and issues with unit cost
 * and amounts and the running balance at moving average cost, kept by the Accounting Division
 * alongside the Supply Division's Stock Card (Appendix 58).
 */
@Component
public class SuppliesLedgerCardForm {

    private static final String FONT = "Times New Roman";

    private final ItemMovements movements;
    private final Signatories signatories;

    public SuppliesLedgerCardForm(ItemMovements movements, Signatories signatories) {
        this.movements = movements;
        this.signatories = signatories;
    }

    public byte[] generate(long itemId, String entityName, String fundCluster) {
        ItemMovements.Card card = movements.of(itemId);
        Map<String, Object> item = card.item();

        Xlsx x = new Xlsx("Supplies Ledger Card");
        x.defaultFont(FONT, 10);
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        // Columns: A Date | B Reference | C-E Receipt | F-H Issue | I-K Balance | L No. of Days to Consume
        double[] widths = {11, 18, 8, 11, 13, 8, 11, 13, 8, 11, 13, 12};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set("L1", "Appendix 57");
        x.style("L1").size(9).italic().right();

        x.merge("A3:L3");
        x.set("A3", "SUPPLIES LEDGER CARD");
        x.style("A3").size(14).bold().center();

        x.set("A5", "Entity Name: " + RpciForm.or(entityName, "___________________________________"));
        x.set("I5", "Fund Cluster: " + RpciForm.or(fundCluster, "_________________"));
        x.set("A7", "Item: " + ItemMovements.str(item.get("name")));
        x.set("I7", "Item Code: " + ItemMovements.str(item.get("code")));
        x.set("A8", "Description: " + ItemMovements.str(item.get("description")));
        x.set("I8", "Re-order Point: " + ItemMovements.str(item.get("min_stock")));
        x.set("A9", "Unit of Measurement: " + ItemMovements.str(item.get("unit_of_measure")));
        x.style("A5:L9").size(10);
        x.style("A5:L5").bold();

        x.set("A11", "Date");
        x.set("B11", "Reference");
        x.set("C11", "Receipt");
        x.set("F11", "Issue");
        x.set("I11", "Balance");
        x.set("L11", "No. of Days to Consume");
        String[] sub = {"Qty.", "Unit Cost", "Total Cost"};
        for (int g = 0; g < 3; g++) for (int i = 0; i < 3; i++) x.set(Xlsx.col(3 + g * 3 + i) + "12", sub[i]);
        x.merge("A11:A12");
        x.merge("B11:B12");
        x.merge("C11:E11");
        x.merge("F11:H11");
        x.merge("I11:K11");
        x.merge("L11:L12");
        x.style("A11:L12").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        int row = 13;
        // Balance brought forward.
        x.set("A" + row, card.lines().isEmpty() ? "" : PropertyCardForm.mdy(card.lines().get(0).date()));
        x.set("B" + row, "Balance forwarded");
        x.set("I" + row, card.openingQty());
        x.set("J" + row, card.openingCost());
        x.set("K" + row, card.openingCost().multiply(BigDecimal.valueOf(Math.max(0, card.openingQty()))));
        styleRow(x, row);
        row++;

        for (ItemMovements.Line l : card.lines()) {
            x.set("A" + row, PropertyCardForm.mdy(l.date()));
            x.set("B" + row, l.reference());
            String q = l.qty() > 0 ? "C" : "F";
            x.set(q + row, Math.abs(l.qty()));
            x.set(Xlsx.col(Xlsx.colIndex(q) + 1) + row, l.unitCost());
            x.set(Xlsx.col(Xlsx.colIndex(q) + 2) + row, l.amount());
            x.set("I" + row, l.balance());
            x.set("J" + row, l.balanceCost());
            x.set("K" + row, l.balanceAmount());
            styleRow(x, row);
            row++;
        }
        for (int i = card.lines().size(); i < 15; i++) {
            x.style("A" + row + ":L" + row).border(Edge.ALL, Line.THIN);
            row++;
        }

        row += 2;
        x.set("A" + row, "Prepared by:");
        x.set("I" + row, "Certified Correct:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("accountant"));
        Signatories.stamp(x, "I" + (row + 1), "I" + (row + 3), signatories.resolve("head_of_agency"));
        x.set("A" + (row + 4), "Accountant / Bookkeeper");
        x.set("I" + (row + 4), "Head of Agency / Authorized Representative");
        x.style("A" + (row + 4) + ":L" + (row + 4)).size(8).italic();

        return x.bytes();
    }

    private static void styleRow(Xlsx x, int row) {
        x.style("A" + row + ":L" + row).border(Edge.ALL, Line.THIN).vcenter();
        x.style("A" + row).size(9).center();
        for (String c : new String[]{"D", "E", "G", "H", "J", "K"}) x.style(c + row).numberFormat("#,##0.00");
        for (String c : new String[]{"C", "F", "I"}) x.style(c + row).center();
    }
}
