package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Disbursement Voucher (DV) — COA Appendix 32: mode of payment, payee, particulars with the
 * deductions, certification A (requesting office), B the accounting entry, C the accountant's
 * certification, D approval for payment by the head of agency, and E the receipt of payment.
 */
@Component
public class DisbursementVoucherForm {

    private static final String FONT = "Arial";

    private final Signatories signatories;

    public DisbursementVoucherForm(Signatories signatories) {
        this.signatories = signatories;
    }

    public byte[] generate(PaymentClaim c) {
        Xlsx x = new Xlsx("DV", FONT, 10);
        double[] widths = {16, 14, 14, 14, 15, 15, 16, 16};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set("H1", "Appendix 32");
        x.style("H1").italic().right();
        x.merge("A2:H2");
        x.set("A2", OrsForm.or(c.entityName(), "____________________________"));
        x.style("A2").bold().underline().center().size(11);
        x.merge("A3:H3");
        x.set("A3", "Entity Name");
        x.style("A3").italic().size(9).center();

        x.merge("A4:E6");
        x.set("A4", "DISBURSEMENT VOUCHER");
        x.style("A4").bold().size(14).center().vcenter();
        meta(x, 4, "Fund Cluster :", OrsForm.or(c.fundCluster(), ""));
        meta(x, 5, "Date :", c.date() == null ? "" : c.date().format(OrsForm.DATE));
        meta(x, 6, "DV No. :", OrsForm.or(c.dvNumber(), ""));
        x.style("A4:H6").border(Edge.OUTLINE, Line.MEDIUM);

        String m = c.paymentMethod();
        x.set("A7", "Mode of Payment");
        x.style("A7").bold();
        x.merge("B7:H7");
        x.set("B7", box("check".equals(m)) + " MDS Check      " + box(false) + " Commercial Check      " + box("ada".equals(m) || "lddap_ada".equals(m))
                + " ADA      " + box(false) + " Others (Please specify) ________");
        x.style("A7:H7").border(Edge.OUTLINE, Line.MEDIUM);

        x.set("A8", "Payee");
        x.style("A8").bold();
        x.merge("B8:D8");
        x.set("B8", OrsForm.or(c.payee(), ""));
        x.set("E8", "TIN/Employee No. :");
        x.set("F8", OrsForm.or(c.tin(), ""));
        x.set("G8", "ORS/BURS No. :");
        x.set("H8", OrsForm.or(c.orsNumber(), ""));
        x.style("E8").size(8).wrap();
        x.style("G8").size(8).wrap();
        x.set("A9", "Address");
        x.style("A9").bold();
        x.merge("B9:H9");
        x.set("B9", OrsForm.or(c.address(), ""));
        x.style("A8:H9").border(Edge.ALL, Line.THIN);
        x.style("A8:H9").border(Edge.OUTLINE, Line.MEDIUM);

        x.merge("A10:D10");
        x.set("A10", "Particulars");
        x.set("E10", "Responsibility Center");
        x.set("F10", "MFO/PAP");
        x.merge("G10:H10");
        x.set("G10", "Amount");
        x.style("A10:H10").bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        x.merge("A11:D11");
        x.set("A11", c.particulars());
        x.set("E11", OrsForm.or(c.responsibilityCenter(), ""));
        x.set("F11", OrsForm.or(c.mfoPap(), ""));
        x.merge("G11:H11");
        x.set("G11", c.amount());
        x.style("A11:H11").valign(V.TOP).wrap();
        x.height(11, 80);
        int r = 12;
        r = less(x, r, "Less: Retention", c.retention());
        r = less(x, r, "Less: Liquidated damages", c.liquidatedDamages());
        r = less(x, r, "Less: Withholding tax", c.withholdingTax());
        x.merge("A" + r + ":F" + r);
        x.set("A" + r, "Amount Due");
        x.style("A" + r).bold().right();
        x.merge("G" + r + ":H" + r);
        x.set("G" + r, c.net());
        x.style("G" + r).bold().border(Edge.TOP, Line.THIN);
        x.style("G11:H" + r).numberFormat(OrsForm.MONEY).right();
        x.style("A10:D" + r).border(Edge.OUTLINE, Line.THIN);
        x.style("E10:E" + r).border(Edge.OUTLINE, Line.THIN);
        x.style("F10:F" + r).border(Edge.OUTLINE, Line.THIN);
        x.style("G10:H" + r).border(Edge.OUTLINE, Line.THIN);
        x.style("A10:H" + r).border(Edge.OUTLINE, Line.MEDIUM);

        // ── A. Certified ───────────────────────────────────────────────
        r += 1;
        int a = r;
        x.merge("A" + r + ":H" + r);
        x.set("A" + r, "A.  Certified: Expenses/Cash Advance necessary, lawful and incurred under my direct supervision.");
        x.style("A" + r).wrap();
        OrsForm.signature(x, r + 1, "B", "C", "F", new Signatories.Signatory(null, null, null, null), "Supervisor / Head, Requesting Office or Authorized Representative");
        x.style("A" + a + ":H" + (r + 4)).border(Edge.OUTLINE, Line.MEDIUM);

        // ── B. Accounting entry ────────────────────────────────────────
        r += 5;
        int b = r;
        x.merge("A" + r + ":H" + r);
        x.set("A" + r, "B.  Accounting Entry:");
        x.style("A" + r).bold();
        r++;
        x.merge("A" + r + ":D" + r);
        x.set("A" + r, "Account Title");
        x.merge("E" + r + ":F" + r);
        x.set("E" + r, "UACS Code");
        x.set("G" + r, "Debit");
        x.set("H" + r, "Credit");
        x.style("A" + r + ":H" + r).bold().center().border(Edge.ALL, Line.THIN);
        List<Object[]> entries = new ArrayList<>();
        entries.add(new Object[]{"Accounts Payable", c.amount(), null});
        if (c.withholdingTax().signum() > 0) entries.add(new Object[]{"Due to BIR", null, c.withholdingTax()});
        if (c.retention().signum() > 0) entries.add(new Object[]{"Guaranty/Security Deposits Payable (retention)", null, c.retention()});
        if (c.liquidatedDamages().signum() > 0) entries.add(new Object[]{"Fines and Penalties (liquidated damages)", null, c.liquidatedDamages()});
        entries.add(new Object[]{"Cash - Modified Disbursement System (MDS), Regular", null, c.net()});
        for (Object[] e : entries) {
            r++;
            x.merge("A" + r + ":D" + r);
            x.set("A" + r, (e[2] != null ? "      " : "") + e[0]);
            x.merge("E" + r + ":F" + r);
            x.set("G" + r, e[1]);
            x.set("H" + r, e[2]);
            x.style("A" + r + ":H" + r).border(Edge.ALL, Line.THIN);
            x.style("G" + r + ":H" + r).numberFormat(OrsForm.MONEY).right();
        }
        x.style("A" + b + ":H" + r).border(Edge.OUTLINE, Line.MEDIUM);

        // ── C. Certified (accountant) and D. Approved for payment ──────
        r += 1;
        int cd = r;
        x.merge("A" + r + ":D" + r);
        x.set("A" + r, "C.  Certified:");
        x.merge("E" + r + ":H" + r);
        x.set("E" + r, "D.  Approved for Payment");
        x.style("A" + r + ":H" + r).bold();
        boolean ready = "for_payment".equals(c.status()) || "paid".equals(c.status());
        String[] checks = {"Cash available", "Subject to Authority to Debit Account (when applicable)", "Supporting documents complete and amount claimed proper"};
        for (int i = 0; i < checks.length; i++) {
            x.merge("A" + (r + 1 + i) + ":D" + (r + 1 + i));
            x.set("A" + (r + 1 + i), "   " + box(false) + "  " + checks[i]);
            x.style("A" + (r + 1 + i)).size(9);
        }
        x.merge("E" + (r + 1) + ":H" + (r + 3));
        x.set("E" + (r + 1), ready ? "Amount: ₱" + new java.text.DecimalFormat("#,##0.00").format(c.net()) : "");
        x.style("E" + (r + 1)).center().vcenter();
        Signatories.Signatory accountant = signatories.resolve("accountant");
        Signatories.Signatory head = signatories.resolve("head_of_agency");
        OrsForm.signature(x, r + 4, "A", "B", "D", accountant, "Head, Accounting Unit/Authorized Representative");
        OrsForm.signature(x, r + 4, "E", "F", "H", head, "Agency Head/Authorized Representative");
        r += 7;
        x.style("A" + cd + ":D" + r).border(Edge.OUTLINE, Line.MEDIUM);
        x.style("E" + cd + ":H" + r).border(Edge.OUTLINE, Line.MEDIUM);

        // ── E. Receipt of payment ──────────────────────────────────────
        r += 1;
        int e = r;
        x.merge("A" + r + ":H" + r);
        x.set("A" + r, "E.  Receipt of Payment");
        x.style("A" + r).bold();
        r++;
        x.set("A" + r, "Check/ADA No. :");
        x.merge("B" + r + ":C" + r);
        x.set("B" + r, OrsForm.or(c.paymentReference(), ""));
        x.set("D" + r, "Date :");
        x.set("E" + r, c.paidDate() == null ? "" : c.paidDate().toString());
        x.set("F" + r, "Bank Name & Account Number :");
        x.merge("G" + r + ":H" + r);
        x.style("F" + r).size(8).wrap();
        x.set("A" + (r + 1), "Signature :");
        x.merge("B" + (r + 1) + ":C" + (r + 1));
        x.set("D" + (r + 1), "Date :");
        x.set("F" + (r + 1), "JEV No. :");
        x.merge("G" + (r + 1) + ":H" + (r + 1));
        x.set("A" + (r + 2), "Printed Name :");
        x.merge("B" + (r + 2) + ":E" + (r + 2));
        x.set("B" + (r + 2), OrsForm.or(c.payee(), ""));
        x.set("F" + (r + 2), "Date :");
        x.merge("G" + (r + 2) + ":H" + (r + 2));
        x.merge("A" + (r + 3) + ":H" + (r + 3));
        x.set("A" + (r + 3), "Official Receipt No. & Date/Other Documents :");
        x.style("A" + r + ":H" + (r + 3)).border(Edge.ALL, Line.THIN).size(9);
        x.height(r + 1, 26);
        r += 3;
        x.style("A" + e + ":H" + r).border(Edge.OUTLINE, Line.MEDIUM);

        x.page().portrait().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.4, 0.4, 0.4, 0.4);
        return x.bytes();
    }

    private static int less(Xlsx x, int r, String label, BigDecimal v) {
        if (v == null || v.signum() == 0) return r;
        x.merge("A" + r + ":D" + r);
        x.set("A" + r, "      " + label);
        x.merge("G" + r + ":H" + r);
        x.set("G" + r, v.negate());
        return r + 1;
    }

    private static void meta(Xlsx x, int r, String label, String value) {
        x.set("F" + r, label);
        x.style("F" + r).bold().right();
        x.merge("G" + r + ":H" + r);
        x.set("G" + r, value);
        x.style("G" + r + ":H" + r).border(Edge.BOTTOM, Line.THIN);
    }

    static String box(boolean on) {
        return on ? "[X]" : "[  ]";
    }
}
