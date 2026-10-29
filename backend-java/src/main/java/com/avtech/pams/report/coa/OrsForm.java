package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Obligation Request and Status (ORS; BURS for off-budget funds) — COA/DBM: payee block, the
 * charge by responsibility center, MFO/PAP and UACS object code, certifications A (requesting
 * office) and B (budget), and C, the status of the obligation.
 */
@Component
public class OrsForm {

    private static final String FONT = "Arial";
    static final String MONEY = "#,##0.00";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final Signatories signatories;

    public OrsForm(Signatories signatories) {
        this.signatories = signatories;
    }

    public byte[] generate(PaymentClaim c) {
        Xlsx x = new Xlsx("ORS", FONT, 10);
        double[] widths = {16, 14, 14, 14, 15, 15, 15, 15};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.merge("A1:H1");
        x.set("A1", "OBLIGATION REQUEST AND STATUS");
        x.style("A1").bold().size(14).center();
        x.merge("A2:H2");
        x.set("A2", or(c.entityName(), "____________________________"));
        x.style("A2").bold().underline().center();
        x.merge("A3:H3");
        x.set("A3", "Entity Name");
        x.style("A3").italic().size(9).center();

        label(x, "F5", "Serial No. :", "G5:H5", or(c.orsNumber(), "______________"));
        label(x, "F6", "Date :", "G6:H6", c.date() == null ? "" : c.date().format(DATE));
        label(x, "F7", "Fund Cluster :", "G7:H7", or(c.fundCluster(), "______________"));

        row(x, 8, "Payee", or(c.payee(), ""));
        row(x, 9, "TIN", or(c.tin(), ""));
        row(x, 10, "Office", "");
        row(x, 11, "Address", or(c.address(), ""));
        x.style("A8:H11").border(Edge.OUTLINE, Line.MEDIUM);

        x.set("A12", "Responsibility Center");
        x.merge("B12:D12");
        x.set("B12", "Particulars");
        x.set("E12", "MFO/PAP");
        x.set("F12", "UACS Object Code");
        x.merge("G12:H12");
        x.set("G12", "Amount");
        x.style("A12:H12").bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.height(12, 28);

        x.set("A13", or(c.responsibilityCenter(), ""));
        x.merge("B13:D13");
        x.set("B13", c.particulars());
        x.set("E13", or(c.mfoPap(), ""));
        x.set("F13", or(c.uacsCode(), ""));
        x.merge("G13:H13");
        x.set("G13", c.amount());
        x.style("A13:H13").valign(V.TOP).wrap().border(Edge.ALL, Line.THIN);
        x.style("G13").numberFormat(MONEY).right();
        x.height(13, 96);
        x.merge("A14:F14");
        x.set("A14", "Total");
        x.style("A14").bold().right();
        x.merge("G14:H14");
        x.set("G14", c.amount());
        x.style("G14").bold().numberFormat(MONEY).right();
        x.style("A14:H14").border(Edge.ALL, Line.THIN);

        // ── Certifications A and B ─────────────────────────────────────
        x.merge("A15:D17");
        x.set("A15", "A.  Certified: Charges to appropriation/allotment are necessary, lawful and under my direct supervision; "
                + "and supporting documents valid, proper and legal.");
        x.merge("E15:H17");
        x.set("E15", "B.  Certified: Allotment available and obligated for the purpose/adjustment necessary as indicated above.");
        x.style("A15:H17").valign(V.TOP).wrap();
        Signatories.Signatory head = signatories.resolve("head_of_agency");
        signature(x, 18, "A", "B", "D", head, "Head, Requesting Office/Authorized Representative");
        signature(x, 18, "E", "F", "H", new Signatories.Signatory(null, null, null, null), "Head, Budget Division/Unit/Authorized Representative");
        x.style("A15:D22").border(Edge.OUTLINE, Line.MEDIUM);
        x.style("E15:H22").border(Edge.OUTLINE, Line.MEDIUM);

        // ── C. Status of obligation ────────────────────────────────────
        x.merge("A24:H24");
        x.set("A24", "C.  STATUS OF OBLIGATION");
        x.style("A24").bold().center();
        x.merge("A25:C25");
        x.set("A25", "Reference");
        x.merge("D25:F25");
        x.set("D25", "Amount");
        x.merge("G25:H25");
        x.set("G25", "Balance");
        String[] heads = {"Date", "Particulars", "ORS/JEV/Check/ADA/TRA No.", "Obligation\n(a)", "Payable\n(b)", "Payment\n(c)", "Not Yet Due\n(a-b)",
                "Due and Demandable\n(b-c)"};
        for (int i = 0; i < heads.length; i++) x.set(Xlsx.col(i + 1) + 26, heads[i]);
        x.style("A25:H26").bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        x.height(26, 30);

        boolean payable = "for_payment".equals(c.status()) || "paid".equals(c.status());
        boolean paid = "paid".equals(c.status());
        BigDecimal a = c.amount();
        BigDecimal b = payable ? c.amount() : BigDecimal.ZERO;
        BigDecimal p = paid ? c.amount() : BigDecimal.ZERO;
        int r = 27;
        status(x, r++, c.date(), "Obligation", c.orsNumber(), a, b, BigDecimal.ZERO);
        if (paid) status(x, r++, c.paidDate(), "Payment", c.paymentReference(), BigDecimal.ZERO, BigDecimal.ZERO, p);
        for (; r < 31; r++) x.style("A" + r + ":H" + r).border(Edge.ALL, Line.THIN);
        x.merge("A" + r + ":C" + r);
        x.set("A" + r, "Total");
        x.set("D" + r, a);
        x.set("E" + r, b);
        x.set("F" + r, p);
        x.set("G" + r, a.subtract(b));
        x.set("H" + r, b.subtract(p));
        x.style("A" + r + ":H" + r).bold().border(Edge.ALL, Line.THIN);
        x.style("D" + r + ":H" + r).numberFormat(MONEY).right();
        x.style("A24:H" + r).border(Edge.OUTLINE, Line.MEDIUM);

        x.page().portrait().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.5, 0.4, 0.5, 0.4);
        return x.bytes();
    }

    private static void status(Xlsx x, int r, LocalDate date, String what, String ref, BigDecimal a, BigDecimal b, BigDecimal p) {
        x.set("A" + r, date == null ? "" : date.toString());
        x.set("B" + r, what);
        x.set("C" + r, ref == null ? "" : ref);
        x.set("D" + r, a.signum() == 0 ? null : a);
        x.set("E" + r, b.signum() == 0 ? null : b);
        x.set("F" + r, p.signum() == 0 ? null : p);
        x.style("A" + r + ":H" + r).border(Edge.ALL, Line.THIN);
        x.style("D" + r + ":H" + r).numberFormat(MONEY).right();
    }

    private static void label(Xlsx x, String cell, String text, String range, String value) {
        x.set(cell, text);
        x.style(cell).bold().right();
        x.merge(range);
        String first = range.split(":")[0];
        x.set(first, value);
        x.style(range).border(Edge.BOTTOM, Line.THIN);
    }

    private static void row(Xlsx x, int r, String label, String value) {
        x.set("A" + r, label);
        x.style("A" + r).bold();
        x.merge("B" + r + ":H" + r);
        x.set("B" + r, value);
        x.style("A" + r + ":H" + r).border(Edge.BOTTOM, Line.THIN);
    }

    /** Signature / Printed Name / Position / Date lines of a certification box, from row {@code r}. */
    static void signature(Xlsx x, int r, String labelCol, String from, String to, Signatories.Signatory s, String position) {
        String[] labels = {"Signature :", "Printed Name :", "Position :", "Date :"};
        for (int i = 0; i < labels.length; i++) {
            x.set(labelCol + (r + i), labels[i]);
            x.style(labelCol + (r + i)).size(9);
            x.merge(from + (r + i) + ":" + to + (r + i));
            x.style(from + (r + i) + ":" + to + (r + i)).border(Edge.BOTTOM, Line.THIN).center();
        }
        Signatories.stamp(x, from + r, from + (r + 1), s, 30);
        x.set(from + (r + 2), position);
        x.style(from + (r + 2)).size(8).wrap();
        x.height(r, 30);
    }

    static String or(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }
}
